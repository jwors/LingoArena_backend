package com.lingoarena.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingoarena.dto.websocket.*;
import com.lingoarena.engine.GameManager;
import com.lingoarena.engine.QuestionGenerator;
import com.lingoarena.engine.ScoringEngine;
import com.lingoarena.entity.*;
import com.lingoarena.enums.GameMode;
import com.lingoarena.enums.RoomStatus;
import com.lingoarena.exception.BusinessException;
import com.lingoarena.exception.ErrorCode;
import com.lingoarena.repository.GameRoomRepository;
import com.lingoarena.websocket.WebSocketSessionManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 游戏服务。
 *
 * 游戏业务逻辑的编排层：
 * 1. 开始游戏前校验条件
 * 2. 调用 GameManager 处理游戏核心逻辑
 * 3. 通过 WebSocket 推送游戏消息（DTO + ObjectMapper 序列化）
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
    private final ObjectMapper objectMapper;

    // ========== 消息推送辅助 ==========

    /** 向房间广播消息 */
    private void broadcast(Long roomId, String type, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(new WebSocketMessage<>(type, payload));
            sessionManager.broadcastToRoom(roomId, json);
        } catch (Exception e) {
            log.error("Failed to serialize WS broadcast: type={}, roomId={}", type, roomId, e);
        }
    }

    /** 向指定用户发送消息 */
    private void sendToUser(Long userId, String type, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(new WebSocketMessage<>(type, payload));
            sessionManager.sendToUser(userId, json);
        } catch (Exception e) {
            log.error("Failed to serialize WS message: type={}, userId={}", type, userId, e);
        }
    }

    private String serializeGameMode(GameMode mode) {
        return mode == GameMode.RACE ? "rush" : "turn_based";
    }

    // ========== 游戏生命周期 ==========

    /**
     * 开始游戏（由房主触发，可从 REST 或 WS 调用）。
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
        int requiredQuestions = room.getGameMode() == GameMode.RACE
                ? room.getTotalRounds()
                : room.getTotalRounds() * 2;
        if (words.size() < requiredQuestions) {
            throw new BusinessException(
                    ErrorCode.WORDBOOK_INSUFFICIENT_WORDS.getCode(),
                    ErrorCode.WORDBOOK_INSUFFICIENT_WORDS.getMessage()
                            + "：需要 " + requiredQuestions + " 个，仅有 " + words.size() + " 个");
        }

        gameManager.startGame(roomId, words, room.getTotalRounds(), room.getGameMode().name(),
                room.getHost().getId(), room.getGuest().getId());

        room.setStatus(RoomStatus.PLAYING);
        room.setStartedAt(LocalDateTime.now());
        gameRoomRepository.save(room);

        // 广播 game:start（DTO 序列化）
        broadcast(roomId, "game:start", GameStartMessage.builder()
                .totalRounds(room.getTotalRounds())
                .gameMode(serializeGameMode(room.getGameMode()))
                .build());

        // 推送第一道题给房主
        pushNextQuestion(roomId, room.getHost().getId());

        log.info("Game started: roomId={}, host={}, guest={}",
                roomId, room.getHost().getId(), room.getGuest().getId());
    }

    /**
     * 从队列弹出下一道题并推送给指定用户。
     * 启动倒计时（含每秒 tick 推送）。
     */
    public boolean pushNextQuestion(Long roomId, Long userId) {
        QuestionGenerator.Question q = gameManager.popNextQuestion(roomId, userId);
        if (q == null) return false;

        int round = gameManager.getCurrentRound(roomId);

        // question:new（DTO 序列化）
        sendToUser(userId, "question:new", NewQuestionMessage.builder()
                .round(round)
                .questionType(q.getType().name().toLowerCase())
                .chinese(q.getWord().getChinese())
                .options(q.getOptions())
                .timeLimit(GameManager.DEFAULT_TIME_LIMIT_SECONDS)
                .build());

        // 广播 turn:start
        broadcast(roomId, "turn:start", TurnStartMessage.builder()
                .currentPlayerId(userId)
                .build());

        // 启动倒计时（每秒 tick + 超时回调）
        gameManager.startRoundTimer(roomId, userId, GameManager.DEFAULT_TIME_LIMIT_SECONDS,
                timeLeft -> broadcast(roomId, "timer:tick", new TimerTickMessage(timeLeft)),
                () -> handleRoundTimeout(roomId, userId));

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

    /** 处理答题后的 WS 消息推送（answer:result + score:update） */
    public void handleAnswerResult(Long roomId, Long userId, GameManager.AnswerCheckResult result) {
        // answer:result → 仅答题者
        sendToUser(userId, "answer:result", RoundResultMessage.builder()
                .correct(result.isCorrect())
                .playerId(userId)
                .answer(result.getCorrectAnswer())
                .build());

        // score:update → 全房间
        broadcast(roomId, "score:update", ScoreUpdateMessage.builder()
                .scores(gameManager.getScores(roomId))
                .build());

        // 广播 opponent:status {submitted} 通知对手已提交
        broadcast(roomId, "opponent:status", OpponentStatusMessage.builder()
                .userId(userId)
                .status("submitted")
                .build());

        // 取消倒计时
        gameManager.cancelRoundTimer(roomId);
    }

    /**
     * 处理超时。
     * 超时玩家视为答错（0 分），直接推进游戏。
     */
    private void handleRoundTimeout(Long roomId, Long userId) {
        log.info("Round timeout: roomId={}, userId={}", roomId, userId);

        // 通知该用户超时
        sendToUser(userId, "answer:result", RoundResultMessage.builder()
                .correct(false)
                .playerId(userId)
                .answer("timeout")
                .build());

        // 更新分数（0 分不扣分）
        broadcast(roomId, "score:update", ScoreUpdateMessage.builder()
                .scores(gameManager.getScores(roomId))
                .build());

        // 检查游戏是否结束
        if (gameManager.isGameOver(roomId)) {
            finishGame(roomId);
            return;
        }

        // 推下一题给另一位玩家
        Long nextUser = gameManager.getOtherPlayerId(roomId, userId);
        if (nextUser != null) {
            pushNextQuestion(roomId, nextUser);
        }
    }

    // ========== 查询方法 ==========

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

    /** 取消玩家准备 */
    public void cancelPlayerReady(Long roomId, Long userId) {
        gameManager.removePlayerReady(roomId, userId);
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

    // ========== 游戏结束 ==========

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

        // 广播 game:end（DTO 序列化，含战绩）
        Map<Long, GameOverMessage.PlayerStats> dtoStats = new java.util.HashMap<>();
        gameManager.getPlayerStats(roomId).forEach((uid, ps) ->
                dtoStats.put(uid, GameOverMessage.PlayerStats.builder()
                        .correct(ps.getCorrect())
                        .wrong(ps.getWrong())
                        .avgTime(ps.getAvgTime())
                        .build()));
        broadcast(roomId, "game:end", GameOverMessage.builder()
                .winner(winnerId)
                .scores(gameManager.getScores(roomId))
                .stats(dtoStats)
                .build());

        // 清理游戏状态
        gameManager.cleanupGame(roomId);

        log.info("Game finished: roomId={}, host={}, guest={}, winner={}",
                roomId, hostScore, guestScore, winnerId);
    }
}
