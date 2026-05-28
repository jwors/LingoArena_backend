package com.lingoarena.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 游戏状态 Redis 存储。
 *
 * 管理两种数据：
 * 1. 题目队列（Redis List）：游戏开始时预生成所有题目，每次从左侧弹出
 * 2. 答案暂存（Redis Hash）：每轮双方提交的答案，等双方都答完再处理
 *
 * 用 Redis 的好处：游戏过程中不需要频繁写 PostgreSQL，
 * 等游戏结束再一次性写入数据库，减少数据库压力。
 */
@Repository
@RequiredArgsConstructor
public class GameStateRepository {

    private static final String QUESTIONS_KEY_PREFIX = "lingoarena:room:";
    private static final String ANSWERS_KEY_PREFIX = "lingoarena:room:";
    private static final long GAME_TTL_HOURS = 2;

    private final RedisTemplate<String, Object> redisTemplate;

    /** 题目队列 Key：lingoarena:room:{roomId}:questions */
    private String questionsKey(Long roomId) {
        return QUESTIONS_KEY_PREFIX + roomId + ":questions";
    }

    /** 答案暂存 Key：lingoarena:room:{roomId}:round:{n}:answers */
    private String answersKey(Long roomId, int round) {
        return ANSWERS_KEY_PREFIX + roomId + ":round:" + round + ":answers";
    }

    /** 往题目队列右侧推入一道题 */
    public void pushQuestion(Long roomId, Object question) {
        String key = questionsKey(roomId);
        redisTemplate.opsForList().rightPush(key, question);
        redisTemplate.expire(key, GAME_TTL_HOURS, TimeUnit.HOURS);
    }

    /** 往题目队列批量推入所有题目（游戏开始时调用） */
    public void pushAllQuestions(Long roomId, List<Object> questions) {
        String key = questionsKey(roomId);
        redisTemplate.opsForList().rightPushAll(key, questions);
        redisTemplate.expire(key, GAME_TTL_HOURS, TimeUnit.HOURS);
    }

    /** 从题目队列左侧弹出一道题（每次弹出即取出） */
    public Object popQuestion(Long roomId) {
        return redisTemplate.opsForList().leftPop(questionsKey(roomId));
    }

    /** 查看队列中剩余的题目数量 */
    public long getRemainingQuestions(Long roomId) {
        Long size = redisTemplate.opsForList().size(questionsKey(roomId));
        return size == null ? 0 : size;
    }

    /** 保存某位玩家某轮的答案 */
    public void saveAnswer(Long roomId, int round, Long userId, Map<String, Object> answer) {
        String key = answersKey(roomId, round);
        redisTemplate.opsForHash().put(key, String.valueOf(userId), answer);
        redisTemplate.expire(key, GAME_TTL_HOURS, TimeUnit.HOURS);
    }

    /** 获取某轮双方的所有答案 */
    public Map<Object, Object> getRoundAnswers(Long roomId, int round) {
        return redisTemplate.opsForHash().entries(answersKey(roomId, round));
    }

    /** 判断某位玩家是否已经答过某轮的题 */
    public boolean hasPlayerAnswered(Long roomId, int round, Long userId) {
        return redisTemplate.opsForHash().hasKey(answersKey(roomId, round), String.valueOf(userId));
    }

    /** 清理游戏状态（游戏结束时调用） */
    public void cleanGameState(Long roomId) {
        redisTemplate.delete(questionsKey(roomId));
    }
}
