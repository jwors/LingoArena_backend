package com.lingoarena.dto.websocket;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SubmitAnswerMessage {
    private int round;
    private String answer;
}
