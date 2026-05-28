package com.lingoarena.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置。
 *
 * 注册 WebSocket 端点：ws://host/ws/room
 * 客户端通过这个地址建立 WebSocket 连接。
 *
 * 配置了 JwtHandshakeInterceptor 在握手阶段验证 JWT token。
 * 注意：roomId 通过 query parameter 传递（?roomId=123&token=xxx），
 * 而不是 URL 路径变量，因为 Spring 原生 WebSocket 注册不支持 {roomId} 模板。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final RoomWebSocketHandler roomWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(roomWebSocketHandler, "/ws/room")
                .addInterceptors(jwtHandshakeInterceptor)   // 在握手时验证 JWT
                .setAllowedOrigins("*");                    // 允许所有来源连接
    }
}
