package com.lingoarena.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 单词实体，对应 words 表。
 * 每个单词属于一个词库（@ManyToOne 关联到 Wordbook）。
 *
 * @ManyToOne 表示多对一关系：多个单词可以属于同一个词库
 * fetch = FetchType.LAZY 表示懒加载——查询单词时不会自动加载词库信息，
 * 只有在主动调用 getWordbook() 时才会查数据库，避免不必要的关联查询
 */
@Entity
@Table(name = "words")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Word {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属词库（懒加载，仅当需要词库信息时才查表） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wordbook_id", nullable = false)
    private Wordbook wordbook;

    /** 英文单词 */
    @Column(nullable = false)
    private String english;

    /** 中文释义 */
    @Column(nullable = false)
    private String chinese;

    /** 音标（可为空，部分词库可能没有音标） */
    private String phonetic;

    /** 在词库中的排序序号 */
    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;
}
