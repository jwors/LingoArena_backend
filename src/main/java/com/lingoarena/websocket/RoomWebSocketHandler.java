package com.lingoarena.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingoarena.engine.GameManager;
import com.lingoarena.service.GameService;
import com.lingoarena.repository.UserRepository;
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
 * 处理游戏阶段的 WebSocket 消息：
 * - 连接/断开：通知对手状态变化
 * - player_ready：标记准备
 * - submit_answer：提交答案
 *
 * 房间管理和游戏开始由 REST API 处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final GameService gameService;
    private final UserRepository userRepository;
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

        // 查询用户昵称，通知对手有人上线了
        String nickname = userRepository.findById(userId)
                .map(user -> user.getNickname())
                .orElse("unknown");
        String escapedNickname = nickname.replace("\\", "\\\\").replace("\"", "\\\"");
        String statusMsg = String.format(
                "{\"type\":\"opponent:status\",\"payload\":{\"userId\":%d,\"nickname\":\"%s\",\"status\":\"connected\"}}",
                userId, escapedNickname);
        sessionManager.broadcastToRoom(roomId, statusMsg);

        log.info("User {} ({}) connected to room {}", userId, nickname, roomId);
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

            if (roomId == null || userId == null) {
                sendError(session, "INVALID_SESSION", "连接无效，缺少用户信息");
                return;
            }

            switch (type) {
                case "player_ready" -> handlePlayerReady(roomId, userId);
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

        if (roomId == null || userId == null) {
            log.debug("Skip cleanup for session without userId/roomId");
            return;
        }

        sessionManager.removeSession(roomId, userId, session);

        // 通知对手有人断线
        String disconnectMsg = String.format(
                "{\"type\":\"opponent:status\",\"payload\":{\"userId\":%d,\"status\":\"disconnected\"}}", userId);
        sessionManager.broadcastToRoom(roomId, disconnectMsg);

        log.info("User {} disconnected from room {}, status: {}", userId, roomId, status);
    }

    /** 处理玩家准备 */
    private void handlePlayerReady(Long roomId, Long userId) {
        gameService.setPlayerReady(roomId, userId);

        // 广播准备状态给房间所有人
        String readyMsg = String.format(
                "{\"type\":\"opponent:status\",\"payload\":{\"userId\":%d,\"status\":\"ready\"}}", userId);
        sessionManager.broadcastToRoom(roomId, readyMsg);
    }

    /** 处理玩家提交答案 */
    private void handleSubmitAnswer(Long roomId, Long userId, JsonNode payload) {
        int round = payload.get("round").asInt();
        String answer = payload.get("answer").asText();
        long timestamp = payload.has("timestamp") ? payload.get("timestamp").asLong() : System.currentTimeMillis();

        // 核验答案
        GameManager.AnswerCheckResult result = gameService.submitAnswer(roomId, userId, round, answer, timestamp);

        if (result.hasError()) {
            sendError(sessionManager.getUserSession(userId), "ANSWER_ERROR", result.getError());
            return;
        }

        // 推送 answer:result 给答题者本人
        String resultMsg = String.format(
                "{\"type\":\"answer:result\",\"payload\":{\"round\":%d,\"correct\":%b,\"correctAnswer\":\"%s\",\"score\":%d}}",
                round, result.isCorrect(), escapeJson(result.getCorrectAnswer()), result.getGainedScore());
        sessionManager.sendToUser(userId, resultMsg);

        // 推送 score:update 给整个房间
        String scoreMsg = String.format(
                "{\"type\":\"score:update\",\"payload\":{\"hostScore\":%d,\"guestScore\":%d}}",
                gameService.getHostScore(roomId), gameService.getGuestScore(roomId));
        sessionManager.broadcastToRoom(roomId, scoreMsg);

        // 检查游戏是否结束
        if (gameService.isGameOver(roomId)) {
            gameService.finishGame(roomId);
            return;
        }

        // 推下一道题给另一位玩家
        Long nextUser = gameService.getOtherPlayerId(roomId, userId);
        if (nextUser != null) {
            gameService.pushNextQuestion(roomId, nextUser);
        }
    }

    /** 给指定连接发送错误消息 */
    private void sendError(WebSocketSession session, String code, String message) {
        if (session == null || !session.isOpen()) return;
        try {
            String errorMsg = String.format(
                    "{\"type\":\"error\",\"payload\":{\"code\":\"%s\",\"message\":\"%s\"}}",
                    code, message);
            session.sendMessage(new TextMessage(errorMsg));
        } catch (IOException e) {
            log.error("Failed to send error message: {}", e.getMessage());
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
