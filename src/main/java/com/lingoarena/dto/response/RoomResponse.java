package com.lingoarena.dto.response;

import com.lingoarena.enums.GameMode;
import com.lingoarena.enums.RoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class RoomResponse {
    private Long id;
    private String roomCode;
    private UserInfo host;
    private UserInfo guest;
    private Long wordbookId;
    private String wordbookName;
    private GameMode gameMode;
    private RoomStatus status;
    private Integer totalRounds;
    private Long winnerId;
    private Integer hostScore;
    private Integer guestScore;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    @Data
    @Builder
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String nickname;
    }
}
