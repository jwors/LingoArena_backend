package com.lingoarena.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 游戏结果实体，对应 game_results 表。
 * 每局游戏结束后，为每位玩家生成一条结果记录。
 * 注意：一局游戏会产生两条结果（房主一条 + 对手一条）。
 */
@Entity
@Table(name = "game_results")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的房间 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private GameRoom room;

    /** 关联的玩家 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 得分 */
    @Column(nullable = false)
    @Builder.Default
    private Integer score = 0;

    /** 答对题数 */
    @Column(name = "correct_count", nullable = false)
    @Builder.Default
    private Integer correctCount = 0;

    /** 答错题数 */
    @Column(name = "wrong_count", nullable = false)
    @Builder.Default
    private Integer wrongCount = 0;

    /** 总题数 */
    @Column(name = "total_questions", nullable = false)
    @Builder.Default
    private Integer totalQuestions = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
