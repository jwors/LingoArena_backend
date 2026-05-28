package com.lingoarena.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class GameResultResponse {
    private Long id;
    private Long roomId;
    private String roomCode;
    private Long userId;
    private String nickname;
    private Integer score;
    private Integer correctCount;
    private Integer wrongCount;
    private Integer totalQuestions;
    private LocalDateTime createdAt;
    private List<RoundInfo> rounds;

    @Data
    @Builder
    @AllArgsConstructor
    public static class RoundInfo {
        private Integer roundNumber;
        private String questionType;
        private String userAnswer;
        private Boolean isCorrect;
        private Integer timeSpentMs;
    }
}
