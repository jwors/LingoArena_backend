package com.lingoarena.repository;

import com.lingoarena.entity.Wordbook;
import com.lingoarena.enums.WordbookLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 词库数据访问层。
 */
public interface WordbookRepository extends JpaRepository<Wordbook, Long> {
    /** 查询所有已启用的词库 */
    List<Wordbook> findByIsActiveTrue();

    /** 按等级查询词库（种子数据与 JSON 文件一一对应） */
    Optional<Wordbook> findByLevel(WordbookLevel level);
}
