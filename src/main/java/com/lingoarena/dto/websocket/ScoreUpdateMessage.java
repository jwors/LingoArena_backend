package com.lingoarena.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 分数更新消息 payload。
 * 格式：{ "scores": { "1": 5, "2": 3 } }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoreUpdateMessage {
    private Map<Long, Integer> scores;
}
