package com.lingoarena.engine;

import com.lingoarena.entity.Word;
import com.lingoarena.enums.RoomStatus;
import com.lingoarena.redis.GameStateRepository;
import com.lingoarena.redis.RoomStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 游戏管理器 - 核心游戏逻辑。
 *
 * 职责：
 * 1. 初始化游戏（生成题目、设置初始状态）
 * 2. 管理题目队列（内存中，每轮按顺序弹出）
 * 3. 处理玩家提交的答案，计分
 * 4. 管理每轮 15 秒超时定时器
 * 5. 判断游戏何时结束
 *
 * 线程安全设计：
 * - 每个房间一个 ReentrantLock，同一时间只有一个线程能修改该房间的状态
 * - 使用 ConcurrentHashMap 管理房间锁和定时器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameManager {

    private final RoomStateRepository roomStateRepository;
    private final GameStateRepository gameStateRepository;
    private final QuestionGenerator questionGenerator;
    private final ScoringEngine scoringEngine;
    private final ScheduledExecutorService gameScheduler;

    /** 每个房间一把锁 */
    private final ConcurrentHashMap<Long, ReentrantLock> roomLocks = new ConcurrentHashMap<>();
    /** 每个房间的超时定时器 */
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> roomTimers = new ConcurrentHashMap<>();

    // ========== 内存游戏状态 ==========

    /** 房间的题目队列（内存中，避免 Redis 序列化问题） */
    private final ConcurrentHashMap<Long, List<QuestionGenerator.Question>> roomQuestions = new ConcurrentHashMap<>();
    /** 当前轮次 */
    private final ConcurrentHashMap<Long, Integer> roomCurrentRound = new ConcurrentHashMap<>();
    /** 房主总分 */
    private final ConcurrentHashMap<Long, Integer> roomHostScore = new ConcurrentHashMap<>();
    /** 对手总分 */
    private final ConcurrentHashMap<Long, Integer> roomGuestScore = new ConcurrentHashMap<>();
    /** 缓存房主 userId */
    private final ConcurrentHashMap<Long, Long> roomHostId = new ConcurrentHashMap<>();
    /** 缓存对手 userId */
    private final ConcurrentHashMap<Long, Long> roomGuestId = new ConcurrentHashMap<>();
    /** 玩家准备状态 */
    private final ConcurrentHashMap<Long, Set<Long>> roomReadyPlayers = new ConcurrentHashMap<>();
    /** 当前题目对应的正确答案，"roomId:userId" -> correctAnswer */
    private final ConcurrentHashMap<String, String> pendingCorrectAnswers = new ConcurrentHashMap<>();

    // ========== 准备状态 ==========

    /** 标记玩家已准备，返回是否双方都已准备 */
    public boolean setPlayerReady(Long roomId, Long userId) {
        roomReadyPlayers.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        boolean bothReady = roomReadyPlayers.get(roomId).size() >= 2;
        log.debug("Player ready: roomId={}, userId={}, bothReady={}", roomId, userId, bothReady);
        return bothReady;
    }

    public boolean areBothReady(Long roomId) {
        Set<Long> ready = roomReadyPlayers.get(roomId);
        return ready != null && ready.size() >= 2;
    }

    public Long getOtherPlayerId(Long roomId, Long userId) {
        Long hostId = roomHostId.get(roomId);
        Long guestId = roomGuestId.get(roomId);
        if (userId.equals(hostId)) return guestId;
        if (userId.equals(guestId)) return hostId;
        return null;
    }

    // ========== 游戏初始化 ==========

    /**
     * 初始化游戏。
     * 1. 从词库中生成题目
     * 2. 存入内存队列
     * 3. 设置初始状态
     */
    public void startGame(Long roomId, List<Word> words, int totalRounds, String gameMode,
                          Long hostId, Long guestId) {
        ReentrantLock lock = getRoomLock(roomId);
        lock.lock();
        try {
            List<QuestionGenerator.Question> questions =
                    questionGenerator.generateQuestions(words, totalRounds, gameMode);

            // 存入内存
            roomQuestions.put(roomId, new ArrayList<>(questions));
            roomCurrentRound.put(roomId, 0);
            roomHostScore.put(roomId, 0);
            roomGuestScore.put(roomId, 0);
            roomHostId.put(roomId, hostId);
            roomGuestId.put(roomId, guestId);

            // 也推入 Redis（用于跨实例扩展，暂保留）
            gameStateRepository.pushAllQuestions(roomId, new ArrayList<>(questions));

            // 初始化 Redis 房间状态
            Map<String, Object> state = new HashMap<>();
            state.put("status", RoomStatus.PLAYING.name());
            state.put("currentRound", 1);
            state.put("totalRounds", totalRounds);
            state.put("gameMode", gameMode);
            state.put("hostScore", 0);
            state.put("guestScore", 0);
            roomStateRepository.saveRoomState(roomId, state);

            log.info("Game initialized: roomId={}, mode={}, rounds={}, questions={}",
                    roomId, gameMode, totalRounds, questions.size());
        } finally {
            lock.unlock();
        }
    }

    // ========== 题目管理 ==========

    /** 弹出下一道题，并记录正确答案 */
    public QuestionGenerator.Question popNextQuestion(Long roomId, Long userId) {
        List<QuestionGenerator.Question> questions = roomQuestions.get(roomId);
        if (questions == null || questions.isEmpty()) return null;

        QuestionGenerator.Question q = questions.remove(0);
        pendingCorrectAnswers.put(roomId + ":" + userId, q.getCorrectAnswer());

        // 房主开始新一轮时递增轮次
        Long hostId = roomHostId.get(roomId);
        if (userId.equals(hostId) || hostId == null) {
            roomCurrentRound.merge(roomId, 1, (old, v) -> old + 1);
        }
        return q;
    }

    /** 获取轮到哪个用户答题（turn-based：房主先答，然后交替） */
    public Long getCurrentTurnUser(Long roomId) {
        int round = roomCurrentRound.getOrDefault(roomId, 1);
        // round 1: host, round 1 guest phase: guest, round 2 host: host...
        // 每轮 2 道题：odd-indexed questions in round = host, even = guest
        long answeredCount = getTotalQuestions(roomId) - getRemainingQuestions(roomId);
        return (answeredCount % 2 == 0) ? roomHostId.get(roomId) : roomGuestId.get(roomId);
    }

    // ========== 答案处理 ==========

    /**
     * 检查答案并返回结果。
     * 同时保存答案到 Redis 供 bothAnswered() 判断。
     */
    public AnswerCheckResult checkAnswer(Long roomId, Long userId, String answer) {
        String correctAnswer = pendingCorrectAnswers.remove(roomId + ":" + userId);
        if (correctAnswer == null) {
            return new AnswerCheckResult(false, 0, null, "没有待批改的题目");
        }

        boolean isCorrect = scoringEngine.checkAnswer(answer, correctAnswer);
        int gainedScore = scoringEngine.calculateScore(isCorrect);
        int round = roomCurrentRound.getOrDefault(roomId, 1);

        // 保存答案到 Redis（供 bothAnswered 判断）
        Map<String, Object> answerData = new HashMap<>();
        answerData.put("answer", answer);
        answerData.put("isCorrect", isCorrect);
        answerData.put("timestampMs", System.currentTimeMillis());
        gameStateRepository.saveAnswer(roomId, round, userId, answerData);

        // 更新内存分数
        if (roomHostId.getOrDefault(roomId, -1L).equals(userId)) {
            roomHostScore.merge(roomId, gainedScore, Integer::sum);
        } else {
            roomGuestScore.merge(roomId, gainedScore, Integer::sum);
        }

        // 同步到 Redis
        roomStateRepository.updateField(roomId, "hostScore", roomHostScore.get(roomId));
        roomStateRepository.updateField(roomId, "guestScore", roomGuestScore.get(roomId));

        return new AnswerCheckResult(isCorrect, gainedScore, correctAnswer, null);
    }

    /** 本轮双方都已答完？ */
    public boolean bothAnswered(Long roomId, int round) {
        Map<Object, Object> answers = gameStateRepository.getRoundAnswers(roomId, round);
        return answers.size() >= 2;
    }

    // ========== 游戏状态 ==========

    public int getCurrentRound(Long roomId) {
        return roomCurrentRound.getOrDefault(roomId, 1);
    }

    /** 前进到下一轮（两个玩家都答题完毕后） */
    public int advanceRound(Long roomId) {
        int next = roomCurrentRound.merge(roomId, 1, (old, v) -> old + 1);
        roomStateRepository.updateField(roomId, "currentRound", next);
        return next;
    }

    /** 剩余题目数 */
    public int getRemainingQuestions(Long roomId) {
        List<QuestionGenerator.Question> questions = roomQuestions.get(roomId);
        return questions == null ? 0 : questions.size();
    }

    /** 总题目数 */
    public int getTotalQuestions(Long roomId) {
        return getRemainingQuestions(roomId);
    }

    /** 游戏是否结束（题目队列为空） */
    public boolean isGameOver(Long roomId) {
        List<QuestionGenerator.Question> questions = roomQuestions.get(roomId);
        return questions == null || questions.isEmpty();
    }

    public int getHostScore(Long roomId) {
        return roomHostScore.getOrDefault(roomId, 0);
    }

    public int getGuestScore(Long roomId) {
        return roomGuestScore.getOrDefault(roomId, 0);
    }

    public Long getHostId(Long roomId) {
        return roomHostId.get(roomId);
    }

    public Long getGuestId(Long roomId) {
        return roomGuestId.get(roomId);
    }

    // ========== 超时管理 ==========

    /**
     * 为本轮设置超时定时器（15 秒）。
     * 如果超时，标记该用户未答并推进游戏。
     */
    public void startRoundTimer(Long roomId, Long userId, Runnable onTimeout) {
        cancelRoundTimer(roomId);
        ScheduledFuture<?> future = gameScheduler.schedule(() -> {
            try {
                onTimeout.run();
            } catch (Exception e) {
                log.error("Round timeout handler error: roomId={}", roomId, e);
            }
        }, 15, TimeUnit.SECONDS);
        roomTimers.put(roomId, future);
    }

    public void cancelRoundTimer(Long roomId) {
        ScheduledFuture<?> existing = roomTimers.remove(roomId);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    // ========== 清理 ==========

    /** 清理游戏状态（游戏结束时调用） */
    public void cleanupGame(Long roomId) {
        roomQuestions.remove(roomId);
        roomCurrentRound.remove(roomId);
        roomHostScore.remove(roomId);
        roomGuestScore.remove(roomId);
        roomHostId.remove(roomId);
        roomGuestId.remove(roomId);
        roomReadyPlayers.remove(roomId);
        pendingCorrectAnswers.keySet().removeIf(k -> k.startsWith(roomId + ":"));
        cancelRoundTimer(roomId);
        roomLocks.remove(roomId);
        gameStateRepository.cleanGameState(roomId);
        roomStateRepository.deleteRoomState(roomId);
        log.info("Game state cleaned up: roomId={}", roomId);
    }

    /** 获取或创建房间锁 */
    private ReentrantLock getRoomLock(Long roomId) {
        return roomLocks.computeIfAbsent(roomId, k -> new ReentrantLock());
    }

    // ========== 结果类 ==========

    /** 答案核验结果 */
    public static class AnswerCheckResult {
        private final boolean correct;
        private final int gainedScore;
        private final String correctAnswer;
        private final String error;

        public AnswerCheckResult(boolean correct, int gainedScore, String correctAnswer, String error) {
            this.correct = correct;
            this.gainedScore = gainedScore;
            this.correctAnswer = correctAnswer;
            this.error = error;
        }

        public boolean isCorrect() { return correct; }
        public int getGainedScore() { return gainedScore; }
        public String getCorrectAnswer() { return correctAnswer; }
        public String getError() { return error; }
        public boolean hasError() { return error != null; }
    }

    /** 提交答案的结果（现有接口，保持兼容） */
    public static class AnswerResult {
        private final boolean success;
        private final String error;

        private AnswerResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }

        public static AnswerResult ok() {
            return new AnswerResult(true, null);
        }

        public static AnswerResult alreadyAnswered() {
            return new AnswerResult(false, "本轮已作答");
        }

        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
}
