package com.lingoarena.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 计分引擎单元测试。
 */
class ScoringEngineTest {

    private ScoringEngine scoringEngine;

    @BeforeEach
    void setUp() {
        scoringEngine = new ScoringEngine();
    }

    @Test
    void calculateScore_correct_shouldReturnTen() {
        assertEquals(10, scoringEngine.calculateScore(true));
    }

    @Test
    void calculateScore_wrong_shouldReturnZero() {
        assertEquals(0, scoringEngine.calculateScore(false));
    }

    @Test
    void checkAnswer_exactMatch_shouldReturnTrue() {
        assertTrue(scoringEngine.checkAnswer("abandon", "abandon"));
    }

    @Test
    void checkAnswer_caseInsensitive_shouldReturnTrue() {
        // 忽略大小写
        assertTrue(scoringEngine.checkAnswer("Abandon", "ABANDON"));
    }

    @Test
    void checkAnswer_trimmed_shouldReturnTrue() {
        // 忽略前后空格
        assertTrue(scoringEngine.checkAnswer("  abandon  ", "abandon"));
    }

    @Test
    void checkAnswer_wrong_shouldReturnFalse() {
        assertFalse(scoringEngine.checkAnswer("abandon", "abnormal"));
    }

    @Test
    void checkAnswer_nullInput_shouldReturnFalse() {
        assertFalse(scoringEngine.checkAnswer(null, "abandon"));
        assertFalse(scoringEngine.checkAnswer("abandon", null));
    }

    @Test
    void timeoutScore_shouldReturnZero() {
        assertEquals(0, scoringEngine.timeoutScore());
    }
}
