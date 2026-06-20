package com.lingoarena.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingoarena.dto.websocket.*;
import com.lingoarena.engine.GameManager;
import com.lingoarena.exception.BusinessException;
import com.lingoarena.service.GameService;
import com.lingoarena.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

/**
 * WebSocket 消息处理器。
 *doc
 * 处理房间阶段的 WebSocket 消息：
 * - 连接时推送 room:joined（房间完整状态）
 * - 连接/断开时广播 opponent:status
 * - player_ready → player:ready_status
 * - player:input → opponent:status typing（透传给对手）
 * - submit_answer → 交 GameService 处理
 * - game:start → 调 GameService（兼容 WS 触发）
 *
 * 所有消息使用 DTO + ObjectMapper 序列化，替代手拼 JSON。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final GameService gameService;
    private final RoomService roomService;
    private final ObjectMapper objectMapper;

    /** 连接建立时调用 */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = sessionManager.getUserIdFromSession(session);
        Long roomId = sessionManager.getRoomIdFromSession(session);

        if (roomId == null || userId == null) {
            log.warn("Connection rejected: missing userId or roomId in session attributes");
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (IOException e) {
                log.error("Failed to close invalid session: {}", e.getMessage());
            }
            return;
        }

        sessionManager.addSession(roomId, userId, session);

        // 发送 room:joined 给连接者（完整房间状态）
        roomService.sendRoomJoined(roomId, userId);

        // 广播 opponent:status {connected} 通知对手
        broadcastToRoom(roomId, "opponent:status",
                OpponentStatusMessage.builder()
                        .userId(userId)
                        .status("connected")
                        .build());

        log.info("User {} connected to room {}", userId, roomId);
    }

    /** 收到消息时调用 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String type = root.has("type") ? root.get("type").asText() : root.path("event").asText(null);
            JsonNode payload = root.has("payload") && !root.get("payload").isNull()
                    ? root.get("payload")
                    : root.get("data");

            Long userId = sessionManager.getUserIdFromSession(session);
            Long roomId = sessionManager.getRoomIdFromSession(session);

            if (roomId == null || userId == null) {
                sendError(session, "INVALID_SESSION", "连接无效，缺少用户信息");
                return;
            }

            if (type == null || type.isBlank()) {
                sendError(session, "UNKNOWN_TYPE", "缺少消息类型");
                return;
            }

            switch (type) {
                case "game:start" -> handleGameStart(roomId, userId);
                case "player:ready" -> handlePlayerReadyToggle(roomId, userId, payload);
                case "player:unready" -> handlePlayerUnready(roomId, userId);
                case "player:input" -> handlePlayerInput(roomId, userId);
                case "answer:submit" -> handleSubmitAnswer(session, roomId, userId, payload);
                default -> sendError(session, "UNKNOWN_TYPE", "未知消息类型: " + type);
            }
        } catch (BusinessException e) {
            log.warn("Business error handling WS message: {}", e.getMessage());
            sendError(session, e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Error handling WS message", e);
            sendError(session, "MESSAGE_PARSE_ERROR", "消息解析失败");
        }
    }

    /** 连接关闭时调用 */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = sessionManager.getUserIdFromSession(session);
        Long roomId = sessionManager.getRoomIdFromSession(session);

        if (roomId == null || userId == null) {
            log.debug("Skip cleanup for session without userId/roomId");
            return;
        }

        sessionManager.removeSession(roomId, userId, session);

        // 广播 opponent:status {disconnected}
        broadcastToRoom(roomId, "opponent:status",
                OpponentStatusMessage.builder()
                        .userId(userId)
                        .status("disconnected")
                        .build());

        log.info("User {} disconnected from room {}, status: {}", userId, roomId, status);
    }

    // ========== 消息处理 ==========

    /** 处理 game:start（兼容 WS 触发） */
    private void handleGameStart(Long roomId, Long userId) {
        try {
            gameService.startGame(roomId, userId);
        } catch (Exception e) {
            log.error("Game start failed: roomId={}, userId={}", roomId, userId, e);
            WebSocketSession session = sessionManager.getUserSession(userId);
            if (session != null) {
                sendError(session, "GAME_START_FAILED", e.getMessage());
            }
        }
    }

    /** 处理玩家准备/取消准备 → 广播 player:ready_status */
    private void handlePlayerReadyToggle(Long roomId, Long userId, JsonNode payload) {
        boolean ready = payload == null || !payload.has("ready") || payload.get("ready").asBoolean(true);
        if (ready) {
            handlePlayerReady(roomId, userId);
        } else {
            handlePlayerUnready(roomId, userId);
        }
    }

    /** 处理玩家准备 → 广播 player:ready_status */
    private void handlePlayerReady(Long roomId, Long userId) {
        gameService.setPlayerReady(roomId, userId);

        broadcastToRoom(roomId, "player:ready_status",
                PlayerReadyMessage.builder()
                        .userId(userId)
                        .ready(true)
                        .build());
    }

    /** 处理玩家取消准备 → 广播 player:ready_status */
    private void handlePlayerUnready(Long roomId, Long userId) {
        gameService.cancelPlayerReady(roomId, userId);

        broadcastToRoom(roomId, "player:ready_status",
                PlayerReadyMessage.builder()
                        .userId(userId)
                        .ready(false)
                        .build());
    }

    /** 处理输入状态提示 → 透传给对手 */
    private void handlePlayerInput(Long roomId, Long userId) {
        broadcastToRoom(roomId, "opponent:status",
                OpponentStatusMessage.builder()
                        .userId(userId)
                        .status("typing")
                        .build());
    }

    /** 处理提交答案 */
    private void handleSubmitAnswer(WebSocketSession session, Long roomId, Long userId, JsonNode payload) {
        if (payload == null || payload.isNull()) {
            sendError(session, "INVALID_PAYLOAD", "缺少 payload");
            return;
        }
        if (!payload.has("answer") || payload.get("answer").isNull()) {
            sendError(session, "INVALID_PAYLOAD", "缺少 answer 字段");
            return;
        }

        String answer = payload.get("answer").asText().trim();
        if (answer.isEmpty()) {
            sendError(session, "INVALID_PAYLOAD", "答案不能为空");
            return;
        }

        int round = gameService.getCurrentRound(roomId);
        long timestamp = payload.has("timestamp") ? payload.get("timestamp").asLong() : System.currentTimeMillis();

        GameManager.AnswerCheckResult result = gameService.submitAnswer(roomId, userId, round, answer, timestamp);

        if (result.hasError()) {
            sendError(session, "ANSWER_ERROR", result.getError());
            return;
        }

        gameService.handleAnswerResult(roomId, userId, result);

        if (gameService.isGameOver(roomId)) {
            gameService.finishGame(roomId);
            return;
        }

        Long nextUser = gameService.getOtherPlayerId(roomId, userId);
        if (nextUser != null) {
            gameService.pushNextQuestion(roomId, nextUser);
        }
    }

    // ========== 消息发送辅助 ==========

    /** 使用 DTO + ObjectMapper 广播消息 */
    private void broadcastToRoom(Long roomId, String type, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(new WebSocketMessage<>(type, payload));
            sessionManager.broadcastToRoom(roomId, json);
        } catch (Exception e) {
            log.error("Failed to serialize WS message: type={}, roomId={}", type, roomId, e);
        }
    }

    /** 给指定连接发送错误消息 */
    private void sendError(WebSocketSession session, String code, String message) {
        if (session == null || !session.isOpen()) return;
        try {
            String json = objectMapper.writeValueAsString(
                    new WebSocketMessage<>("error", ErrorMessage.builder()
                            .code(code)
                            .message(message)
                            .build()));
            sessionManager.sendToSession(session, json);
        } catch (Exception e) {
            log.error("Failed to send error message: {}", e.getMessage());
        }
    }
}
