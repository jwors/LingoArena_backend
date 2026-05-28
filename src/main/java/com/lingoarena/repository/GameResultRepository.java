package com.lingoarena.repository;

import com.lingoarena.entity.GameResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 游戏结果数据访问层。
 */
public interface GameResultRepository extends JpaRepository<GameResult, Long> {
    /** 获取某个玩家的历史记录（按时间倒序） */
    Page<GameResult> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    /** 获取某个玩家的全部结果（用于统计） */
    List<GameResult> findByUserId(Long userId);
}
