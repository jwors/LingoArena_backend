package com.lingoarena.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 答题倒计时消息 payload。
 * 格式：{ "timeLeft": 10 }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimerTickMessage {
    private int timeLeft;
}
