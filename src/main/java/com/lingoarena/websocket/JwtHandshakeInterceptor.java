package com.lingoarena.websocket;

import com.lingoarena.entity.GameRoom;
import com.lingoarena.repository.GameRoomRepository;
import com.lingoarena.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

/**
 * WebSocket 握手拦截器。
 *
 * 为什么需要这个？
 * Spring Security 的过滤器链只拦截 HTTP 请求，不拦截 WebSocket 升级握手。
 * 所以必须在这里单独验证 JWT token，否则任何人都能连上 WebSocket。
 *
 * 验证流程：
 * 1. 从 URL query 中提取 token 以及 roomId 或 roomCode
 * 2. 调用 JwtTokenService 验证 token 有效性
 * 3. 验证通过后将 userId 和 roomId 存入 session attributes
 * 4. 后续 RoomWebSocketHandler 可以从 attributes 中获取这些信息
 *
 * 支持两种连接方式：
 * - 数字 roomId：ws://localhost:8080/ws/room?roomId=1&token=xxx
 * - 6 位房间码：ws://localhost:8080/ws/room?roomCode=ABC123&token=xxx
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenService jwtTokenService;
    private final GameRoomRepository gameRoomRepository;

    /**
     * 握手前调用。返回 true 表示允许连接，false 表示拒绝。
     *
     * 支持两种连接方式：
     * 1. 通过数字 roomId：ws://.../ws/room?roomId=1&token=xxx
     * 2. 通过 6 位房间码：ws://.../ws/room?roomCode=ABC123&token=xxx
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        try {
            URI uri = request.getURI();
            String query = uri.getQuery();

            if (query == null || !query.contains("token=")) {
                log.warn("WebSocket handshake rejected: missing token");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            String token = extractQueryParam(query, "token");
            if (token == null || !jwtTokenService.validateToken(token)) {
                log.warn("WebSocket handshake rejected: invalid token");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            // 解析 roomId：优先取 roomId 参数，回退到通过 roomCode 查找
            Long roomId = resolveRoomId(query);
            if (roomId == null) {
                log.warn("WebSocket handshake rejected: missing roomId or roomCode");
                response.setStatusCode(HttpStatus.BAD_REQUEST);
                return false;
            }

            Long userId = jwtTokenService.getUserIdFromToken(token);

            // 重要：这些信息会传给 RoomWebSocketHandler，通过 session.getAttributes() 获取
            attributes.put("userId", userId);
            attributes.put("roomId", roomId);

            log.debug("WebSocket handshake success: userId={}, roomId={}", userId, roomId);
            return true;
        } catch (Exception e) {
            log.error("WebSocket handshake error: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    /** 解析 roomId：尝试 roomId 参数 → roomCode 回退查找 */
    private Long resolveRoomId(String query) {
        String roomIdStr = extractQueryParam(query, "roomId");
        if (roomIdStr != null) {
            try {
                return Long.parseLong(roomIdStr);
            } catch (NumberFormatException e) {
                log.warn("Invalid roomId format: {}", roomIdStr);
                return null;
            }
        }

        String roomCode = extractQueryParam(query, "roomCode");
        if (roomCode != null) {
            Optional<GameRoom> room = gameRoomRepository.findByRoomCode(roomCode);
            if (room.isPresent()) {
                return room.get().getId();
            }
            log.warn("Room not found for roomCode: {}", roomCode);
        }

        return null;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 不需要额外操作
    }

    /** 从 URL query string 中解析指定参数的值 */
    private String extractQueryParam(String query, String param) {
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2 && keyValue[0].equals(param)) {
                return keyValue[1];
            }
        }
        return null;
    }
}
