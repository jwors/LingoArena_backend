package com.lingoarena.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对手游戏内状态消息 payload（用于 typing/submitted）。
 * 格式：{ "userId": 1, "status": "typing"|"submitted" }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpponentStatusMessage {
    private Long userId;
    private String status;
}
