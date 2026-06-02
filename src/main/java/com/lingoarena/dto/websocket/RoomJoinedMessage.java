package com.lingoarena.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 房间加入消息 payload（WS 连接成功后推送）。
 * 格式：{ "players": [...], "hostId": 1, "wordBook": {...}|null, "roomCode": "ABC123", "status": "WAITING" }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomJoinedMessage {
    private List<PlayerInfo> players;
    private Long hostId;
    private WordBookInfo wordBook;
    private String roomCode;
    private String status;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlayerInfo {
        private long id;
        private String nickname;
        private boolean isHost;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WordBookInfo {
        private long id;
        private String name;
    }
}
