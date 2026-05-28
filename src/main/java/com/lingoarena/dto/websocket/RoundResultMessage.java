package com.lingoarena.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoundResultMessage {
    private int round;
    private String correctAnswer;
    private List<PlayerResult> results;
    private List<PlayerScore> scores;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlayerResult {
        private long userId;
        private String answer;
        private boolean isCorrect;
        private int timeSpentMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlayerScore {
        private long userId;
        private int score;
    }
}
