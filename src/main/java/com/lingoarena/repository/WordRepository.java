package com.lingoarena.repository;

import com.lingoarena.entity.Word;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 单词数据访问层。
 */
public interface WordRepository extends JpaRepository<Word, Long> {
    /** 分页查询某个词库的单词列表 */
    Page<Word> findByWordbookId(Long wordbookId, Pageable pageable);
    /** 查询某个词库的全部单词（用于游戏初始化） */
    List<Word> findByWordbookId(Long wordbookId);
    /** 统计某个词库的单词总数 */
    long countByWordbookId(Long wordbookId);

    /** 清空词库单词（用于重新导入种子数据） */
    @Transactional
    void deleteByWordbookId(Long wordbookId);
}
