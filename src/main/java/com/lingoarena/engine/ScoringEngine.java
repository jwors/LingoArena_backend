package com.lingoarena.engine;

import org.springframework.stereotype.Component;

/**
 * 计分引擎。
 *
 * 评分规则：
 * - 答对 +10 分
 * - 答错或超时 0 分（不扣分）
 *
 * MVP 阶段使用简单计分，后续可扩展：
 * - 速度奖励（先答对的多加分）
 * - 连击奖励（连续答对有额外加分）
 * - 难度系数（不同题型不同分值）
 */
@Component
public class ScoringEngine {

    private static final int CORRECT_SCORE = 10;
    private static final int WRONG_SCORE = 0;
    private static final int TIMEOUT_SCORE = 0;

    /** 根据是否答对计算得分 */
    public int calculateScore(boolean isCorrect) {
        return isCorrect ? CORRECT_SCORE : WRONG_SCORE;
    }

    /**
     * 判断答案是否正确。
     * 忽略大小写和前后空格，例如 "Abandon  " 和 "abandon" 视为相等。
     */
    public boolean checkAnswer(String userAnswer, String correctAnswer) {
        if (userAnswer == null || correctAnswer == null) {
            return false;
        }
        return userAnswer.trim().equalsIgnoreCase(correctAnswer.trim());
    }

    /** 超时得分（固定 0 分） */
    public int timeoutScore() {
        return TIMEOUT_SCORE;
    }

    /** 答对得分值 */
    public int getCorrectScore() {
        return CORRECT_SCORE;
    }
}
