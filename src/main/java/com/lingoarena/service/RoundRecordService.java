package com.lingoarena.service;

import com.lingoarena.entity.*;
import com.lingoarena.enums.QuestionType;
import com.lingoarena.repository.GameResultRepository;
import com.lingoarena.repository.RoundRecordRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 回合记录服务。
 *
 * 负责游戏结束时，将游戏过程中的数据持久化到数据库：
 * - round_records：每轮每人的答题详情
 * - game_results：每人每局的汇总结果
 *
 * 游戏过程中的数据存在 Redis（快速读写），游戏结束后通过这里写入 PostgreSQL（持久存储）。
 */
@Service
@RequiredArgsConstructor
public class RoundRecordService {

    private final RoundRecordRepository roundRecordRepository;
    private final GameResultRepository gameResultRepository;

    /** 保存单轮答题记录 */
    @Transactional
    public void saveRoundRecord(GameRoom room, User user, Word word,
                                 QuestionType questionType, String userAnswer,
                                 boolean isCorrect, int timeSpentMs, int roundNumber) {
        RoundRecord record = RoundRecord.builder()
                .room(room)
                .roundNumber(roundNumber)
                .user(user)
                .word(word)
                .questionType(questionType)
                .userAnswer(userAnswer)
                .isCorrect(isCorrect)
                .timeSpentMs(timeSpentMs)
                .build();
        roundRecordRepository.save(record);
    }

    /** 保存游戏结果汇总 */
    @Transactional
    public void saveGameResult(GameRoom room, User user, int score,
                                int correctCount, int wrongCount, int totalQuestions) {
        GameResult result = GameResult.builder()
                .room(room)
                .user(user)
                .score(score)
                .correctCount(correctCount)
                .wrongCount(wrongCount)
                .totalQuestions(totalQuestions)
                .build();
        gameResultRepository.save(result);
    }
}
