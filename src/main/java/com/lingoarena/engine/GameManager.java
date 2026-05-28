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
 * 2. 处理玩家提交的答案
 * 3. 管理每轮 15 秒超时计时器
 * 4. 判断游戏何时结束
 *
 * 线程安全设计：
 * - 每个房间一个 ReentrantLock，同一时间只有一个线程能修改该房间的状态
 * - 使用 ConcurrentHashMap 管理房间锁和定时器
 * - 多人抢答时不会出现数据竞争
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

    /** 每个房间一把锁，key=roomId, value=锁 */
    private final ConcurrentHashMap<Long, ReentrantLock> roomLocks = new ConcurrentHashMap<>();
    /** 每个房间的超时定时器，key=roomId, value=定时任务句柄 */
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> roomTimers = new ConcurrentHashMap<>();

    /**
     * 初始化游戏。
     * 1. 从词库中生成题目
     * 2. 题目存入 Redis List
     * 3. 房间状态设为 PLAYING
     */
    public void startGame(Long roomId, List<Word> words, int totalRounds, String gameMode) {
        ReentrantLock lock = getRoomLock(roomId);
        lock.lock();
        try {
            List<QuestionGenerator.Question> questions =
                    questionGenerator.generateQuestions(words, totalRounds, gameMode);

            // 所有题目批量推入 Redis List
            gameStateRepository.pushAllQuestions(roomId, new ArrayList<>(questions));

            // 初始化房间状态
            Map<String, Object> state = new HashMap<>();
            state.put("status", RoomStatus.PLAYING.name());
            state.put("currentRound", 1);
            state.put("totalRounds", totalRounds);
            state.put("gameMode", gameMode);
            state.put("hostScore", 0);
            state.put("guestScore", 0);
            roomStateRepository.saveRoomState(roomId, state);

            log.info("Game started: roomId={}, mode={}, rounds={}", roomId, gameMode, totalRounds);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 处理玩家提交答案。
     * 先检查是否已答过（防止重复提交），然后保存答案到 Redis。
     */
    public AnswerResult submitAnswer(Long roomId, int round, Long userId, String answer,
                                     long timestampMs, String gameMode) {
        ReentrantLock lock = getRoomLock(roomId);
        lock.lock();
        try {
            // 防重复提交
            if (gameStateRepository.hasPlayerAnswered(roomId, round, userId)) {
                return AnswerResult.alreadyAnswered();
            }

            // 暂存答案到 Redis
            Map<String, Object> answerData = new HashMap<>();
            answerData.put("answer", answer);
            answerData.put("timestampMs", timestampMs);
            gameStateRepository.saveAnswer(roomId, round, userId, answerData);

            return AnswerResult.ok();
        } finally {
            lock.unlock();
        }
    }

    /** 检查双方是否都已提交答案 */
    public boolean bothAnswered(Long roomId, int round) {
        Map<Object, Object> answers = gameStateRepository.getRoundAnswers(roomId, round);
        return answers.size() >= 2;
    }

    public int getTimeoutScore() {
        return scoringEngine.timeoutScore();
    }

    /** 获取或创建房间锁 */
    private ReentrantLock getRoomLock(Long roomId) {
        return roomLocks.computeIfAbsent(roomId, k -> new ReentrantLock());
    }

    /**
     * 提交答案的结果。
     * ok() = 成功，alreadyAnswered() = 重复提交被拒绝
     */
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
