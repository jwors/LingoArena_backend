package com.lingoarena.engine;

import com.lingoarena.entity.Word;
import com.lingoarena.enums.QuestionType;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 题目生成器。
 *
 * 游戏开始时调用 generateQuestions()，从选中的词库中随机抽题。
 *
 * 生成逻辑：
 * 1. 从词库中随机抽取 N 个不重复的单词
 * 2. 每个单词 50% 概率为拼写题，50% 为选择题
 * 3. 选择题从词库其他单词中随机选 3 个作为干扰项
 * 4. 题目顺序和选项顺序都随机打乱
 */
@Component
public class QuestionGenerator {

    /** 选择题选项数（4 选 1） */
    private static final int CHOICE_OPTIONS_COUNT = 4;
    /** 拼写题出现概率（50%） */
    private static final double SPELL_PROBABILITY = 0.5;
    private final Random random = new Random();

    /**
     * 生成一轮游戏的题目列表。
     *
     * @param words       词库中的所有单词
     * @param totalRounds 总轮数
     * @param gameMode    游戏模式（TURN_BASED 用 2*rounds 题，RACE 用 rounds 题）
     * @return 题目列表
     */
    public List<Question> generateQuestions(List<Word> words, int totalRounds, String gameMode) {
        int questionCount = "RACE".equals(gameMode) ? totalRounds : totalRounds * 2;

        if (words.size() < questionCount) {
            throw new IllegalArgumentException(
                    "词库单词数不足：需要 " + questionCount + " 个，仅有 " + words.size() + " 个");
        }

        // 随机抽取不重复的单词
        List<Word> shuffled = new ArrayList<>(words);
        Collections.shuffle(shuffled, random);
        List<Word> selected = shuffled.subList(0, questionCount);

        // 剩余的单词用于生成选择题的干扰项
        List<Word> remaining = new ArrayList<>(shuffled.subList(questionCount, shuffled.size()));

        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < questionCount; i++) {
            Word word = selected.get(i);
            // 随机决定题型：50% 拼写，50% 选择
            QuestionType type = random.nextDouble() < SPELL_PROBABILITY
                    ? QuestionType.SPELL
                    : QuestionType.CHOICE;

            List<String> options = null;
            if (type == QuestionType.CHOICE) {
                options = generateOptions(word, remaining);
            }

            questions.add(new Question(word, type, options));
        }

        return questions;
    }

    /**
     * 生成选择题的选项。
     * 从干扰词池中选 3 个，加上正确答案，共 4 个选项，打乱顺序。
     */
    private List<String> generateOptions(Word correctWord, List<Word> distractors) {
        List<Word> pool = new ArrayList<>(distractors);
        pool.removeIf(w -> w.getId().equals(correctWord.getId()));
        Collections.shuffle(pool, random);

        List<String> options = pool.stream()
                .limit(CHOICE_OPTIONS_COUNT - 1)
                .map(Word::getEnglish)
                .collect(Collectors.toList());

        // 加入正确答案并打乱顺序
        options.add(correctWord.getEnglish());
        Collections.shuffle(options, random);

        return options;
    }

    /**
     * 题目对象。
     * 包含原单词引用、题型、选项（选择题才有）。
     * 注意：这是一个内部类，通过 getter 访问字段。
     */
    public static class Question {
        private final Word word;         // 对应的单词
        private final QuestionType type; // 题型
        private final List<String> options; // 选项（拼写题为 null）

        public Question(Word word, QuestionType type, List<String> options) {
            this.word = word;
            this.type = type;
            this.options = options;
        }

        public Word getWord() { return word; }
        public QuestionType getType() { return type; }
        public List<String> getOptions() { return options; }

        /** 题目提示语：显示中文释义 */
        public String getPrompt() {
            return word.getChinese();
        }

        /** 正确答案：英文单词 */
        public String getCorrectAnswer() {
            return word.getEnglish();
        }
    }
}
