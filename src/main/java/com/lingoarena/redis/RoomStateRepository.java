package com.lingoarena.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 房间状态 Redis 存储。
 *
 * 为什么不在 PostgreSQL 里存游戏中的状态？
 * 游戏中的状态变化非常频繁（每轮都可能更新分数、答案等），
 * 用 Redis 的 Hash 结构可以做到：
 * 1. 读写速度快（内存操作）
 * 2. 自动过期（2 小时无操作自动清理）
 * 3. 灵活修改字段（不需要改数据库表结构）
 *
 * Key 格式：lingoarena:room:{roomId}  （Hash 类型）
 * 包含字段：status, hostScore, guestScore, currentRound 等
 */
@Repository
@RequiredArgsConstructor
public class RoomStateRepository {

    /** Redis key 前缀，加命名空间避免和其他项目冲突 */
    private static final String ROOM_KEY_PREFIX = "lingoarena:room:";
    /** 房间状态自动过期时间（2 小时） */
    private static final long ROOM_TTL_HOURS = 2;

    private final RedisTemplate<String, Object> redisTemplate;

    /** 构造完整的 Redis key */
    private String roomKey(Long roomId) {
        return ROOM_KEY_PREFIX + roomId;
    }

    /** 保存整个房间状态（会覆盖已有值） */
    public void saveRoomState(Long roomId, Map<String, Object> state) {
        redisTemplate.opsForHash().putAll(roomKey(roomId), state);
        redisTemplate.expire(roomKey(roomId), ROOM_TTL_HOURS, TimeUnit.HOURS);
    }

    /** 获取房间所有状态字段 */
    public Map<Object, Object> getRoomState(Long roomId) {
        return redisTemplate.opsForHash().entries(roomKey(roomId));
    }

    /** 更新单个字段 */
    public void updateField(Long roomId, String field, Object value) {
        redisTemplate.opsForHash().put(roomKey(roomId), field, value);
    }

    /** 读取单个字段 */
    public Object getField(Long roomId, String field) {
        return redisTemplate.opsForHash().get(roomKey(roomId), field);
    }

    /** 删除房间状态（游戏结束时清理） */
    public void deleteRoomState(Long roomId) {
        redisTemplate.delete(roomKey(roomId));
    }
}
