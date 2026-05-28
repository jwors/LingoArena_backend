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
public class NewQuestionMessage {
    private int round;
    private String questionType;
    private String prompt;
    private List<String> options;
    private int timeLimit;
}
