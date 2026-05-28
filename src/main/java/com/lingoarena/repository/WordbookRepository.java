package com.lingoarena.repository;

import com.lingoarena.entity.Wordbook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 词库数据访问层。
 */
public interface WordbookRepository extends JpaRepository<Wordbook, Long> {
    /** 查询所有已启用的词库 */
    List<Wordbook> findByIsActiveTrue();
}
