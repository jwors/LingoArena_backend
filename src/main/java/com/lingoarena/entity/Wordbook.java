package com.lingoarena.entity;

import com.lingoarena.enums.WordbookLevel;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 词库实体，对应 wordbooks 表。
 * 一条记录就是一个词库，例如"四级核心词汇"。
 * 每个词库下包含多个单词（由 Word 实体关联）。
 */
@Entity
@Table(name = "wordbooks")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wordbook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 词库名称，如"四级核心词汇" */
    @Column(nullable = false)
    private String name;

    /** 词库描述 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 词库等级（CET4 / CET6 / KAOYAN / IELTS / TOEFL） */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WordbookLevel level;

    /** 是否启用（可用来下架旧词库） */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
