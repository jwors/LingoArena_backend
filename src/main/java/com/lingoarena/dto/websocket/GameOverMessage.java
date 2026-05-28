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
public class GameOverMessage {
    private Long winnerId;
    private List<PlayerScoreSummary> finalScores;
    private PlayerSummary summary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlayerScoreSummary {
        private long userId;
        private int score;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlayerSummary {
        private int totalQuestions;
        private int correctCount;
        private int wrongCount;
    }
}
