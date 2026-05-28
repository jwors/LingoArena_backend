package com.lingoarena.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 通用消息包装。
 * 所有消息使用统一格式：{ "type": "xxx", "payload": {...} }
 *
 * @param <T> payload 的具体类型（如 NewQuestionMessage, RoundResultMessage 等）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage<T> {
    private String type;
    private T payload;
}
