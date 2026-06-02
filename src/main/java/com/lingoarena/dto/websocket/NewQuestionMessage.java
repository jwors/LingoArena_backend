package com.lingoarena.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 出题消息 payload。
 * 格式：{ "round": 1, "questionType": "spell"|"choice", "chinese": "苹果", "options": [...], "timeLimit": 15 }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewQuestionMessage {
    private int round;
    private String questionType;
    private String chinese;
    private List<String> options;
    private int timeLimit;
}
