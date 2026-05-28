package com.lingoarena.repository;

import com.lingoarena.entity.RoundRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 回合记录数据访问层。
 */
public interface RoundRecordRepository extends JpaRepository<RoundRecord, Long> {
    /** 根据房间 ID 查找所有回合记录（用于游戏复盘） */
    List<RoundRecord> findByRoomId(Long roomId);
}
