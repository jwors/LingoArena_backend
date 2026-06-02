package com.lingoarena.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 房间关闭消息 payload。
 * 格式：{ "roomId": 1 }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomClosedMessage {
    private Long roomId;
}
