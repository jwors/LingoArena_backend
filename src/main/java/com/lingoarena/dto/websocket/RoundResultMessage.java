package com.lingoarena.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 答题结果消息 payload。
 * 格式：{ "correct": true, "playerId": 1, "answer": "apple" }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoundResultMessage {
    private boolean correct;
    private Long playerId;
    private String answer;
}
