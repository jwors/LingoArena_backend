package com.lingoarena.entity;

import com.lingoarena.enums.GameMode;
import com.lingoarena.enums.RoomStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 游戏房间实体，对应 game_rooms 表。
 * 一局游戏对应一条记录，包含玩家、词库、比分等全部信息。
 *
 * 核心字段说明：
 * - roomCode: 6位房间码，玩家通过这个码加入房间（不暴露内部 ID）
 * - host/guest: 房主和对手，都是 User 的关联
 * - gameMode: 游戏模式（回合制/抢答制）
 * - status: 房间生命周期状态
 */
@Entity
@Table(name = "game_rooms")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 6 位房间码（用于分享和加入，不暴露自增 ID） */
    @Column(name = "room_code", nullable = false, unique = true, length = 6)
    private String roomCode;

    /** 房主（创建房间的人） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    /** 对手（加入房间的人，可为空直到有人加入） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    private User guest;

    /** 选中的词库（由房主选择，可为空直到选定） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wordbook_id")
    private Wordbook wordbook;

    /** 游戏模式（默认回合制） */
    @Enumerated(EnumType.STRING)
    @Column(name = "game_mode", nullable = false)
    @Builder.Default
    private GameMode gameMode = GameMode.TURN_BASED;

    /** 房间状态（等待中/游戏中/已结束/已取消） */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RoomStatus status = RoomStatus.WAITING;

    /** 总轮数（默认 10 轮） */
    @Column(name = "total_rounds", nullable = false)
    @Builder.Default
    private Integer totalRounds = 10;

    /** 胜者（平局时为空） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

    /** 房主最终得分 */
    @Column(name = "host_score", nullable = false)
    @Builder.Default
    private Integer hostScore = 0;

    /** 对手最终得分 */
    @Column(name = "guest_score", nullable = false)
    @Builder.Default
    private Integer guestScore = 0;

    /** 房间创建时间 */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 游戏开始时间 */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 游戏结束时间 */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
