package com.lingoarena.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 游戏结束消息 payload。
 * 格式：{
 *   "winner": 1,             // null 表示平局
 *   "scores": { "1": 5, "2": 3 },
 *   "stats": {
 *     "1": { "correct": 5, "wrong": 0, "avgTime": 2.1 },
 *     "2": { "correct": 3, "wrong": 2, "avgTime": 3.5 }
 *   }
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameOverMessage {
    private Long winner;
    private Map<Long, Integer> scores;
    private Map<Long, PlayerStats> stats;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlayerStats {
        private int correct;
        private int wrong;
        private double avgTime;
    }
}
