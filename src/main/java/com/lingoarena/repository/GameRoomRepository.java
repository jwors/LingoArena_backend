package com.lingoarena.repository;

import com.lingoarena.entity.GameRoom;
import com.lingoarena.enums.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 游戏房间数据访问层。
 */
public interface GameRoomRepository extends JpaRepository<GameRoom, Long> {
    /** 根据 6 位房间码查找房间 */
    Optional<GameRoom> findByRoomCode(String roomCode);
    /** 判断房间码是否已被使用 */
    boolean existsByRoomCode(String roomCode);
    /** 查找超时未开始的房间（用于定时清理） */
    List<GameRoom> findByStatusAndCreatedAtBefore(RoomStatus status, LocalDateTime time);

    /** 加载房间及 host/guest/wordbook（避免 WS 回调中 LazyInitializationException） */
    @Query("SELECT r FROM GameRoom r "
            + "JOIN FETCH r.host "
            + "LEFT JOIN FETCH r.guest "
            + "LEFT JOIN FETCH r.wordbook "
            + "WHERE r.id = :id")
    Optional<GameRoom> findByIdWithDetails(Long id);
}
