package com.lingoarena.service;

import com.lingoarena.engine.GameManager;
import com.lingoarena.engine.QuestionGenerator;
import com.lingoarena.engine.ScoringEngine;
import com.lingoarena.entity.*;
import com.lingoarena.enums.RoomStatus;
import com.lingoarena.exception.BusinessException;
import com.lingoarena.exception.ErrorCode;
import com.lingoarena.repository.*;
import com.lingoarena.websocket.WebSocketSessionManager;
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
 * 1. 开始游戏前校验条件
 * 2. 调用 GameManager 处理游戏核心逻辑
 * 3. 通过 WebSocket 推送游戏消息
 * 4. 游戏结束后持久化结果到数据库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRoomRepository gameRoomRepository;
    private final GameManager gameManager;
    private final ScoringEngine scoringEngine;
    private final WordbookService wordbookService;
    private final WebSocketSessionManager sessionManager;

    /**
     * 开始游戏（由房主通过 REST 触发）。
     */
    @Transactional
    public void startGame(Long roomId, Long hostId) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND.getCode(),
                        ErrorCode.ROOM_NOT_FOUND.getMessage()));

        if (!room.getHost().getId().equals(hostId)) {
            throw new BusinessException(ErrorCode.NOT_ROOM_HOST.getCode(),
                    ErrorCode.NOT_ROOM_HOST.getMessage());
        }
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_STARTED.getCode(),
                    ErrorCode.ROOM_ALREADY_STARTED.getMessage());
        }
        if (room.getGuest() == null) {
            throw new BusinessException("ROOM_NOT_FULL", "房间人数不足，无法开始游戏");
        }
        if (!gameManager.areBothReady(roomId)) {
            throw new BusinessException("PLAYER_NOT_READY", "双方都未准备");
        }
        if (room.getWordbook() == null) {
            throw new BusinessException("WORDBOOK_NOT_SELECTED", "未选择词库");
        }

        List<Word> words = wordbookService.getAllWords(room.getWordbook().getId());

        gameManager.startGame(roomId, words, room.getTotalRounds(), room.getGameMode().name(),
                room.getHost().getId(), room.getGuest().getId());

        room.setStatus(RoomStatus.PLAYING);
        room.setStartedAt(LocalDateTime.now());
        gameRoomRepository.save(room);

        // 广播 game:start
        String startMsg = String.format(
                "{\"type\":\"game:start\",\"payload\":{\"totalRounds\":%d,\"gameMode\":\"%s\"}}",
                room.getTotalRounds(), room.getGameMode().name());
        sessionManager.broadcastToRoom(roomId, startMsg);

        // 推送第一道题给房主
        pushNextQuestion(roomId, room.getHost().getId());

        log.info("Game started: roomId={}, host={}, guest={}",
                roomId, room.getHost().getId(), room.getGuest().getId());
    }

    /**
     * 从队列弹出下一道题并推送给指定用户。
     * 返回 true 表示还有题，false 表示已无题。
     */
    public boolean pushNextQuestion(Long roomId, Long userId) {
        QuestionGenerator.Question q = gameManager.popNextQuestion(roomId, userId);
        if (q == null) return false;

        int round = gameManager.getCurrentRound(roomId);
        String typeName = q.getType().name().toLowerCase();
        String content = q.getWord().getChinese();

        // 构建 options（选择题有选项，拼写题为 null）
        String optionsJson = "null";
        if (q.getOptions() != null) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < q.getOptions().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(q.getOptions().get(i))).append("\"");
            }
            sb.append("]");
            optionsJson = sb.toString();
        }

        String questionMsg = String.format(
                "{\"type\":\"question:new\",\"payload\":{\"round\":%d,\"questionType\":\"%s\",\"content\":\"%s\",\"options\":%s}}",
                round, typeName, escapeJson(content), optionsJson);
        sessionManager.sendToUser(userId, questionMsg);
        return true;
    }

    /**
     * 处理答案提交，返回核验结果。
     */
    public GameManager.AnswerCheckResult submitAnswer(Long roomId, Long userId, int round,
                                                       String answer, long timestampMs) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND.getCode(),
                        ErrorCode.ROOM_NOT_FOUND.getMessage()));

        if (room.getStatus() != RoomStatus.PLAYING) {
            throw new BusinessException(ErrorCode.GAME_NOT_STARTED.getCode(),
                    ErrorCode.GAME_NOT_STARTED.getMessage());
        }

        return gameManager.checkAnswer(roomId, userId, answer);
    }

    /** 检查双方是否已作答 */
    public boolean bothAnswered(Long roomId, int round) {
        return gameManager.bothAnswered(roomId, round);
    }

    /** 游戏是否结束 */
    public boolean isGameOver(Long roomId) {
        return gameManager.isGameOver(roomId);
    }

    /** 获取当前轮次 */
    public int getCurrentRound(Long roomId) {
        return gameManager.getCurrentRound(roomId);
    }

    /** 推进到下一轮 */
    public int advanceRound(Long roomId) {
        return gameManager.advanceRound(roomId);
    }

    /** 标记玩家已准备 */
    public void setPlayerReady(Long roomId, Long userId) {
        gameManager.setPlayerReady(roomId, userId);
    }

    /** 获取房主分数 */
    public int getHostScore(Long roomId) {
        return gameManager.getHostScore(roomId);
    }

    /** 获取对手分数 */
    public int getGuestScore(Long roomId) {
        return gameManager.getGuestScore(roomId);
    }

    /** 获取房间中另一位玩家的 userId */
    public Long getOtherPlayerId(Long roomId, Long userId) {
        return gameManager.getOtherPlayerId(roomId, userId);
    }

    /** 结束游戏，写入数据库，广播 game:end */
    @Transactional
    public void finishGame(Long roomId) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND.getCode(),
                        ErrorCode.ROOM_NOT_FOUND.getMessage()));

        int hostScore = gameManager.getHostScore(roomId);
        int guestScore = gameManager.getGuestScore(roomId);

        room.setHostScore(hostScore);
        room.setGuestScore(guestScore);
        room.setStatus(RoomStatus.FINISHED);
        room.setFinishedAt(LocalDateTime.now());

        Long winnerId = null;
        if (hostScore > guestScore) {
            room.setWinner(room.getHost());
            winnerId = room.getHost().getId();
        } else if (guestScore > hostScore) {
            room.setWinner(room.getGuest());
            winnerId = room.getGuest().getId();
        }

        gameRoomRepository.save(room);

        // 广播 game:end
        String winnerIdStr = winnerId != null ? String.valueOf(winnerId) : "null";
        String endMsg = String.format(
                "{\"type\":\"game:end\",\"payload\":{\"winnerId\":%s,\"hostScore\":%d,\"guestScore\":%d}}",
                winnerIdStr, hostScore, guestScore);
        sessionManager.broadcastToRoom(roomId, endMsg);

        // 清理游戏状态
        gameManager.cleanupGame(roomId);

        log.info("Game finished: roomId={}, host={}, guest={}, winner={}",
                roomId, hostScore, guestScore, winnerId);
    }

    /** JSON 字符串转义 */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
