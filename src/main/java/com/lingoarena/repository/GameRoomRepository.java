package com.lingoarena.repository;

import com.lingoarena.entity.GameRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 游戏房间数据访问层。
 */
public interface GameRoomRepository extends JpaRepository<GameRoom, Long> {
    /** 根据 6 位房间码查找房间 */
    Optional<GameRoom> findByRoomCode(String roomCode);
    /** 判断房间码是否已被使用 */
    boolean existsByRoomCode(String roomCode);
}
