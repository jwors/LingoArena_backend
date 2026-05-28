package com.lingoarena.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingoarena.service.GameService;
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
 *
 * 处理 WebSocket 连接生命周期和消息：
 * - 连接建立：将 session 注册到 SessionManager
 * - 消息到达：解析 JSON，根据 type 分发到不同处理方法
 * - 连接断开：清理 session，通知对手
 *
 * 消息格式：{"type": "消息类型", "payload": {...}}
 * 和 REST API 不同，WebSocket 消息用 type 字段区分用途，而不是 URL 路径。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final GameService gameService;
    private final ObjectMapper objectMapper;

    /** 连接建立时调用 */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = sessionManager.getUserIdFromSession(session);
        Long roomId = sessionManager.getRoomIdFromSession(session);

        sessionManager.addSession(roomId, userId, session);

        // 通知房间内的所有人有人加入了
        String joinMsg = String.format(
                "{\"type\":\"room_joined\",\"payload\":{\"user\":{\"id\":%d}}}", userId);
        sessionManager.broadcastToRoom(roomId, joinMsg);

        log.info("User {} connected to room {}", userId, roomId);
    }

    /** 收到消息时调用 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String type = root.get("type").asText();
            JsonNode payload = root.get("payload");

            Long userId = sessionManager.getUserIdFromSession(session);
            Long roomId = sessionManager.getRoomIdFromSession(session);

            // 根据消息类型分发处理
            switch (type) {
                case "player_ready" -> handlePlayerReady(roomId, userId);
                case "select_wordbook" -> handleSelectWordbook(roomId, userId, payload);
                case "submit_answer" -> handleSubmitAnswer(roomId, userId, payload);
                default -> sendError(session, "UNKNOWN_TYPE", "未知消息类型: " + type);
            }
        } catch (Exception e) {
            log.error("Error handling message: {}", e.getMessage());
            sendError(session, "MESSAGE_PARSE_ERROR", "消息解析失败");
        }
    }

    /** 连接关闭时调用 */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = sessionManager.getUserIdFromSession(session);
        Long roomId = sessionManager.getRoomIdFromSession(session);

        sessionManager.removeSession(roomId, userId, session);

        // 通知对手
        String disconnectMsg = String.format(
                "{\"type\":\"opponent_disconnected\",\"payload\":{\"userId\":%d}}", userId);
        sessionManager.broadcastToRoom(roomId, disconnectMsg);

        log.info("User {} disconnected from room {}, status: {}", userId, roomId, status);
    }

    /** 处理玩家准备 */
    private void handlePlayerReady(Long roomId, Long userId) {
        String msg = String.format(
                "{\"type\":\"player_ready_ack\",\"payload\":{\"userId\":%d,\"ready\":true}}", userId);
        sessionManager.broadcastToRoom(roomId, msg);
    }

    /** 房主选择词库 */
    private void handleSelectWordbook(Long roomId, Long userId, JsonNode payload) {
        String msg = String.format(
                "{\"type\":\"select_wordbook_ack\",\"payload\":{\"wordbookId\":%d}}",
                payload.get("wordbookId").asLong());
        sessionManager.broadcastToRoom(roomId, msg);
    }

    /** 处理玩家提交答案 */
    private void handleSubmitAnswer(Long roomId, Long userId, JsonNode payload) {
        int round = payload.get("round").asInt();
        String answer = payload.get("answer").asText();

        gameService.submitAnswer(roomId, userId, round, answer, System.currentTimeMillis());

        // 如果双方都答完了，广播本轮结果
        if (gameService.bothAnswered(roomId, round)) {
            String resultMsg = String.format(
                    "{\"type\":\"round_result\",\"payload\":{\"round\":%d}}", round);
            sessionManager.broadcastToRoom(roomId, resultMsg);
        }
    }

    /** 给指定连接发送错误消息 */
    private void sendError(WebSocketSession session, String code, String message) {
        try {
            String errorMsg = String.format(
                    "{\"type\":\"error\",\"payload\":{\"code\":\"%s\",\"message\":\"%s\"}}",
                    code, message);
            session.sendMessage(new TextMessage(errorMsg));
        } catch (IOException e) {
            log.error("Failed to send error message: {}", e.getMessage());
        }
    }
}
