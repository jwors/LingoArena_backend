package com.lingoarena.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 轮次开始消息 payload。
 * 格式：{ "currentPlayerId": "1" }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurnStartMessage {
    private Long currentPlayerId;
}
