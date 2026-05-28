package com.lingoarena.entity;

import com.lingoarena.enums.QuestionType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 回合记录实体，对应 round_records 表。
 * 每局游戏中每个玩家的每次作答都会生成一条记录。
 * 这是最细粒度的数据，用于复盘查看每道题的答题情况。
 */
@Entity
@Table(name = "round_records")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoundRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的房间 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private GameRoom room;

    /** 第几轮（从 1 开始） */
    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    /** 作答的玩家 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 对应的单词 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;

    /** 题目类型（拼写题或选择题） */
    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private QuestionType questionType;

    /** 玩家的答案 */
    @Column(name = "user_answer", length = 500)
    private String userAnswer;

    /** 是否正确 */
    @Column(name = "is_correct", nullable = false)
    @Builder.Default
    private Boolean isCorrect = false;

    /** 耗时（毫秒） */
    @Column(name = "time_spent_ms", nullable = false)
    @Builder.Default
    private Integer timeSpentMs = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
