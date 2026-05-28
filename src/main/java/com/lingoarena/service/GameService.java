package com.lingoarena.service;

import com.lingoarena.entity.*;
import com.lingoarena.enums.RoomStatus;
import com.lingoarena.exception.BusinessException;
import com.lingoarena.exception.ErrorCode;
import com.lingoarena.repository.*;
import com.lingoarena.engine.GameManager;
import com.lingoarena.engine.ScoringEngine;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 游戏服务。
 *
 * 游戏业务逻辑的编排层：
 * 1. 开始游戏前校验条件（房主身份、房间状态、词库是否选择等）
 * 2. 调用 GameManager 处理游戏核心逻辑
 * 3. 游戏结束后持久化结果到数据库
 *
 * 注意：真正的游戏引擎逻辑在 engine 包中，这里只是编排和校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRoomRepository gameRoomRepository;
    private final GameManager gameManager;
    private final ScoringEngine scoringEngine;
    private final WordbookService wordbookService;

    /**
     * 开始游戏（由房主触发）。
     * 会校验各种前置条件，然后初始化游戏引擎。
     */
    @Transactional
    public void startGame(Long roomId, Long hostId) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND.getCode(),
                        ErrorCode.ROOM_NOT_FOUND.getMessage()));

        // 校验：必须是房主
        if (!room.getHost().getId().equals(hostId)) {
            throw new BusinessException(ErrorCode.NOT_ROOM_HOST.getCode(),
                    ErrorCode.NOT_ROOM_HOST.getMessage());
        }
        // 校验：房间状态必须为等待中
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_STARTED.getCode(),
                    ErrorCode.ROOM_ALREADY_STARTED.getMessage());
        }
        // 校验：必须满员
        if (room.getGuest() == null) {
            throw new BusinessException("ROOM_NOT_FULL", "房间人数不足");
        }
        // 校验：必须选择了词库
        if (room.getWordbook() == null) {
            throw new BusinessException("WORDBOOK_NOT_SELECTED", "未选择词库");
        }

        // 获取词库单词
        List<Word> words = wordbookService.getAllWords(room.getWordbook().getId());

        // 初始化游戏引擎（生成题目等）
        gameManager.startGame(roomId, words, room.getTotalRounds(), room.getGameMode().name());

        // 更新房间状态
        room.setStatus(RoomStatus.PLAYING);
        room.setStartedAt(LocalDateTime.now());
        gameRoomRepository.save(room);

        log.info("Game started: roomId={}, userId={}", roomId, hostId);
    }

    /** 处理玩家提交答案 */
    public GameManager.AnswerResult submitAnswer(Long roomId, Long userId, int round,
                                                  String answer, long timestampMs) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND.getCode(),
                        ErrorCode.ROOM_NOT_FOUND.getMessage()));

        if (room.getStatus() != RoomStatus.PLAYING) {
            throw new BusinessException(ErrorCode.GAME_NOT_STARTED.getCode(),
                    ErrorCode.GAME_NOT_STARTED.getMessage());
        }

        return gameManager.submitAnswer(roomId, round, userId, answer, timestampMs,
                room.getGameMode().name());
    }

    /** 检查本轮双方是否都已作答 */
    public boolean bothAnswered(Long roomId, int round) {
        return gameManager.bothAnswered(roomId, round);
    }

    /**
     * 结束游戏，将结果写入数据库。
     * 更新房间的比分、状态，判定胜者。
     */
    @Transactional
    public void finishGame(Long roomId, int hostScore, int guestScore) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND.getCode(),
                        ErrorCode.ROOM_NOT_FOUND.getMessage()));

        room.setHostScore(hostScore);
        room.setGuestScore(guestScore);
        room.setStatus(RoomStatus.FINISHED);
        room.setFinishedAt(LocalDateTime.now());

        // 判定胜者
        if (hostScore > guestScore) {
            room.setWinner(room.getHost());
        } else if (guestScore > hostScore) {
            room.setWinner(room.getGuest());
        }
        // 平局：winner 留 null

        gameRoomRepository.save(room);
        log.info("Game finished: roomId={}, host={}, guest={}", roomId, hostScore, guestScore);
    }
}
