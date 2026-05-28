package com.lingoarena.engine;

import com.lingoarena.entity.Word;
import com.lingoarena.entity.Wordbook;
import com.lingoarena.enums.QuestionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 题目生成器单元测试。
 *
 * JUnit 5 常用断言：
 * - assertEquals(expected, actual)    判断相等
 * - assertNotNull(obj)                判断不为空
 * - assertTrue(condition)             判断为真
 * - assertThrows(Exception.class, () -> ...)  判断抛出了指定异常
 */
class QuestionGeneratorTest {

    private QuestionGenerator generator;
    private List<Word> words;

    /** @BeforeEach 在每个测试方法运行前执行，用来初始化测试数据 */
    @BeforeEach
    void setUp() {
        generator = new QuestionGenerator();
        words = new ArrayList<>();

        // 创建一个包含 100 个单词的模拟词库
        Wordbook wordbook = Wordbook.builder().id(1L).build();
        for (int i = 0; i < 100; i++) {
            words.add(Word.builder()
                    .id((long) i)
                    .wordbook(wordbook)
                    .english("word" + i)
                    .chinese("单词" + i)
                    .build());
        }
    }

    @Test
    void generateQuestions_raceMode_shouldGenerateCorrectCount() {
        // 抢答模式：10 轮 = 10 题
        List<QuestionGenerator.Question> questions = generator.generateQuestions(words, 10, "RACE");
        assertEquals(10, questions.size());
    }

    @Test
    void generateQuestions_turnBasedMode_shouldGenerateDoubleCount() {
        // 回合制：10 轮 = 20 题（每人 10 题）
        List<QuestionGenerator.Question> questions = generator.generateQuestions(words, 10, "TURN_BASED");
        assertEquals(20, questions.size());
    }

    @Test
    void generateQuestions_noDuplicateWords() {
        // 生成的题目中的单词应该不重复
        List<QuestionGenerator.Question> questions = generator.generateQuestions(words, 10, "RACE");
        long uniqueWordIds = questions.stream()
                .map(q -> q.getWord().getId())
                .distinct()
                .count();
        assertEquals(questions.size(), uniqueWordIds);
    }

    @Test
    void generateQuestions_choiceQuestionShouldHaveOptions() {
        // 选择题应该有 4 个选项，且包含正确答案
        List<QuestionGenerator.Question> questions = generator.generateQuestions(words, 10, "RACE");
        for (QuestionGenerator.Question q : questions) {
            if (q.getType() == QuestionType.CHOICE) {
                assertNotNull(q.getOptions());
                assertEquals(4, q.getOptions().size());
                assertTrue(q.getOptions().contains(q.getCorrectAnswer()));
            }
        }
    }

    @Test
    void generateQuestions_spellQuestionShouldHaveNoOptions() {
        // 拼写题应该没有选项
        List<QuestionGenerator.Question> questions = generator.generateQuestions(words, 10, "RACE");
        for (QuestionGenerator.Question q : questions) {
            if (q.getType() == QuestionType.SPELL) {
                assertNull(q.getOptions());
            }
        }
    }

    @Test
    void generateQuestions_insufficientWords_shouldThrow() {
        // 词库单词数不足时应该抛出异常
        List<Word> fewWords = words.subList(0, 5);
        assertThrows(IllegalArgumentException.class,
                () -> generator.generateQuestions(fewWords, 10, "RACE"));
    }
}
