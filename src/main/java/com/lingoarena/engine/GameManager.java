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
import java.util.function.Consumer;

/**
 * 游戏管理器 - 核心游戏逻辑。
 *
 * 职责：
 * 1. 初始化游戏（生成题目、设置初始状态）
 * 2. 管理题目队列（内存中，每轮按顺序弹出）
 * 3. 处理玩家提交的答案，计分并统计正误/响应时间
 * 4. 管理每轮倒计时（含每秒 tick 推送）
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

    public static final int DEFAULT_TIME_LIMIT_SECONDS = 15;

    /** 每个房间一把锁 */
    private final ConcurrentHashMap<Long, ReentrantLock> roomLocks = new ConcurrentHashMap<>();
    /** 每个房间的超时定时器 */
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> roomTimers = new ConcurrentHashMap<>();
    /** 每个房间的 tick 定时器（每秒推送倒计时） */
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> roomTickTimers = new ConcurrentHashMap<>();

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
    /** 当前题目的推送时间，"roomId:userId" -> pushTimeMs */
    private final ConcurrentHashMap<String, Long> questionPushTimes = new ConcurrentHashMap<>();

    // ========== 战绩统计 ==========

    /** 答对次数，"roomId" -> {userId -> count} */
    private final ConcurrentHashMap<Long, Map<Long, Integer>> roomCorrectCounts = new ConcurrentHashMap<>();
    /** 答错次数，"roomId" -> {userId -> count} */
    private final ConcurrentHashMap<Long, Map<Long, Integer>> roomWrongCounts = new ConcurrentHashMap<>();
    /** 响应时间，"roomId" -> {userId -> [timeMs, ...]} */
    private final ConcurrentHashMap<Long, Map<Long, List<Long>>> roomResponseTimes = new ConcurrentHashMap<>();

    // ========== 准备状态 ==========

    /** 标记玩家已准备，返回是否双方都已准备 */
    public boolean setPlayerReady(Long roomId, Long userId) {
        roomReadyPlayers.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        boolean bothReady = roomReadyPlayers.get(roomId).size() >= 2;
        log.debug("Player ready: roomId={}, userId={}, bothReady={}", roomId, userId, bothReady);
        return bothReady;
    }

    /** 取消玩家准备 */
    public void removePlayerReady(Long roomId, Long userId) {
        Set<Long> ready = roomReadyPlayers.get(roomId);
        if (ready != null) {
            ready.remove(userId);
            log.debug("Player unready: roomId={}, userId={}", roomId, userId);
        }
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
            roomCorrectCounts.put(roomId, new ConcurrentHashMap<>());
            roomWrongCounts.put(roomId, new ConcurrentHashMap<>());
            roomResponseTimes.put(roomId, new ConcurrentHashMap<>());

            // Redis 缓存为可选；题目队列以内存为准，序列化 JPA 实体易失败，不得阻断开局
            try {
                gameStateRepository.pushAllQuestions(roomId, new ArrayList<>(questions));
                Map<String, Object> state = new HashMap<>();
                state.put("status", RoomStatus.PLAYING.name());
                state.put("currentRound", 1);
                state.put("totalRounds", totalRounds);
                state.put("gameMode", gameMode);
                state.put("hostScore", 0);
                state.put("guestScore", 0);
                roomStateRepository.saveRoomState(roomId, state);
            } catch (Exception e) {
                log.warn("Failed to persist game state to Redis for roomId={}, continuing in-memory: {}",
                        roomId, e.getMessage());
            }

            log.info("Game initialized: roomId={}, mode={}, rounds={}, questions={}",
                    roomId, gameMode, totalRounds, questions.size());
        } finally {
            lock.unlock();
        }
    }

    // ========== 题目管理 ==========

    /** 弹出下一道题，记录正确答案并记下推送时间 */
    public QuestionGenerator.Question popNextQuestion(Long roomId, Long userId) {
        List<QuestionGenerator.Question> questions = roomQuestions.get(roomId);
        if (questions == null || questions.isEmpty()) return null;

        QuestionGenerator.Question q = questions.remove(0);
        pendingCorrectAnswers.put(roomId + ":" + userId, q.getCorrectAnswer());
        questionPushTimes.put(roomId + ":" + userId, System.currentTimeMillis());

        // 房主开始新一轮时递增轮次
        Long hostId = roomHostId.get(roomId);
        if (userId.equals(hostId) || hostId == null) {
            roomCurrentRound.merge(roomId, 1, (old, v) -> old + 1);
        }
        return q;
    }

    // ========== 答案处理 ==========

    /**
     * 检查答案并返回结果。
     * 同时更新分数和战绩统计。
     */
    public AnswerCheckResult checkAnswer(Long roomId, Long userId, String answer) {
        ReentrantLock lock = getRoomLock(roomId);
        lock.lock();
        try {
            String key = roomId + ":" + userId;
            String correctAnswer = pendingCorrectAnswers.remove(key);
            if (correctAnswer == null) {
                return new AnswerCheckResult(false, 0, null, "没有待批改的题目");
            }

            boolean isCorrect = scoringEngine.checkAnswer(answer, correctAnswer);
            int gainedScore = scoringEngine.calculateScore(isCorrect);
            int round = roomCurrentRound.getOrDefault(roomId, 1);

            Map<String, Object> answerData = new HashMap<>();
            answerData.put("answer", answer);
            answerData.put("isCorrect", isCorrect);
            answerData.put("timestampMs", System.currentTimeMillis());
            try {
                gameStateRepository.saveAnswer(roomId, round, userId, answerData);
            } catch (Exception e) {
                log.warn("Failed to save answer to Redis: roomId={}, userId={}, {}", roomId, userId, e.getMessage());
            }

            if (roomHostId.getOrDefault(roomId, -1L).equals(userId)) {
                roomHostScore.merge(roomId, gainedScore, Integer::sum);
            } else {
                roomGuestScore.merge(roomId, gainedScore, Integer::sum);
            }

            try {
                roomStateRepository.updateField(roomId, "hostScore", roomHostScore.get(roomId));
                roomStateRepository.updateField(roomId, "guestScore", roomGuestScore.get(roomId));
            } catch (Exception e) {
                log.warn("Failed to sync scores to Redis: roomId={}, {}", roomId, e.getMessage());
            }

            recordAnswer(roomId, userId, isCorrect);
            recordResponseTime(roomId, userId);

            return new AnswerCheckResult(isCorrect, gainedScore, correctAnswer, null);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 超时处理：若该题尚未作答则标记为超时并返回 true；已作答则返回 false。
     */
    public boolean consumeTimeout(Long roomId, Long userId) {
        ReentrantLock lock = getRoomLock(roomId);
        lock.lock();
        try {
            String key = roomId + ":" + userId;
            if (!pendingCorrectAnswers.containsKey(key)) {
                return false;
            }
            pendingCorrectAnswers.remove(key);
            questionPushTimes.remove(key);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** 内存中是否有进行中的游戏（与 DB 状态可能短暂不一致） */
    public boolean isGameInProgress(Long roomId) {
        return roomQuestions.containsKey(roomId);
    }

    /** 本轮双方都已答完？ */
    public boolean bothAnswered(Long roomId, int round) {
        Map<Object, Object> answers = gameStateRepository.getRoundAnswers(roomId, round);
        return answers.size() >= 2;
    }

    // ========== 战绩统计 ==========

    private void recordAnswer(Long roomId, Long userId, boolean isCorrect) {
        Map<Long, Integer> counts = isCorrect
                ? roomCorrectCounts.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                : roomWrongCounts.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());
        counts.merge(userId, 1, Integer::sum);
    }

    private void recordResponseTime(Long roomId, Long userId) {
        Long pushTime = questionPushTimes.remove(roomId + ":" + userId);
        if (pushTime == null) return;

        long elapsed = System.currentTimeMillis() - pushTime;
        roomResponseTimes
                .computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(elapsed);
    }

    /**
     * 获取双方战绩统计。
     * 返回 { userId -> { correct, wrong, avgTime } }。
     */
    public Map<Long, PlayerStats> getPlayerStats(Long roomId) {
        Map<Long, Integer> corrects = roomCorrectCounts.getOrDefault(roomId, Collections.emptyMap());
        Map<Long, Integer> wrongs = roomWrongCounts.getOrDefault(roomId, Collections.emptyMap());
        Map<Long, List<Long>> times = roomResponseTimes.getOrDefault(roomId, Collections.emptyMap());

        Set<Long> userIds = new HashSet<>();
        userIds.addAll(corrects.keySet());
        userIds.addAll(wrongs.keySet());
        userIds.addAll(times.keySet());
        // 确保双方都在结果中（即使一方全对/全错）
        Long hostId = roomHostId.get(roomId);
        Long guestId = roomGuestId.get(roomId);
        if (hostId != null) userIds.add(hostId);
        if (guestId != null) userIds.add(guestId);

        Map<Long, PlayerStats> result = new HashMap<>();
        for (Long uid : userIds) {
            int correct = corrects.getOrDefault(uid, 0);
            int wrong = wrongs.getOrDefault(uid, 0);
            List<Long> timeList = times.getOrDefault(uid, Collections.emptyList());
            double avgTime = timeList.isEmpty() ? 0.0
                    : timeList.stream().mapToLong(Long::longValue).average().orElse(0.0) / 1000.0;
            result.put(uid, new PlayerStats(correct, wrong, avgTime));
        }
        return result;
    }

    /** 获取双方总分映射 */
    public Map<Long, Integer> getScores(Long roomId) {
        Map<Long, Integer> scores = new HashMap<>();
        Long hostId = roomHostId.get(roomId);
        Long guestId = roomGuestId.get(roomId);
        if (hostId != null) scores.put(hostId, roomHostScore.getOrDefault(roomId, 0));
        if (guestId != null) scores.put(guestId, roomGuestScore.getOrDefault(roomId, 0));
        return scores;
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
     * 为本轮设置倒计时。
     *
     * @param roomId           房间 ID
     * @param userId           当前答题用户 ID
     * @param timeLimitSeconds 倒计时秒数
     * @param onTick           每秒回调，参数为剩余秒数
     * @param onTimeout        超时回调
     */
    public void startRoundTimer(Long roomId, Long userId, int timeLimitSeconds,
                                 Consumer<Integer> onTick, Runnable onTimeout) {
        cancelRoundTimer(roomId);

        // 每秒 tick
        final int[] remaining = {timeLimitSeconds};
        ScheduledFuture<?> tickFuture = gameScheduler.scheduleAtFixedRate(() -> {
            remaining[0]--;
            if (remaining[0] >= 0 && onTick != null) {
                try {
                    onTick.accept(remaining[0]);
                } catch (Exception e) {
                    log.error("Timer tick error: roomId={}", roomId, e);
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
        roomTickTimers.put(roomId, tickFuture);

        // 超时
        ScheduledFuture<?> timeoutFuture = gameScheduler.schedule(() -> {
            cancelRoundTimer(roomId);
            if (onTimeout != null) {
                try {
                    onTimeout.run();
                } catch (Exception e) {
                    log.error("Round timeout error: roomId={}", roomId, e);
                }
            }
        }, timeLimitSeconds, TimeUnit.SECONDS);
        roomTimers.put(roomId, timeoutFuture);
    }

    public void cancelRoundTimer(Long roomId) {
        ScheduledFuture<?> existing = roomTimers.remove(roomId);
        if (existing != null) existing.cancel(false);

        ScheduledFuture<?> tickExisting = roomTickTimers.remove(roomId);
        if (tickExisting != null) tickExisting.cancel(false);
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
        roomCorrectCounts.remove(roomId);
        roomWrongCounts.remove(roomId);
        roomResponseTimes.remove(roomId);
        pendingCorrectAnswers.keySet().removeIf(k -> k.startsWith(roomId + ":"));
        questionPushTimes.keySet().removeIf(k -> k.startsWith(roomId + ":"));
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

    /** 玩家战绩 */
    public static class PlayerStats {
        private final int correct;
        private final int wrong;
        private final double avgTime;

        public PlayerStats(int correct, int wrong, double avgTime) {
            this.correct = correct;
            this.wrong = wrong;
            this.avgTime = avgTime;
        }

        public int getCorrect() { return correct; }
        public int getWrong() { return wrong; }
        public double getAvgTime() { return avgTime; }
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
