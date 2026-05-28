package com.lingoarena.service;

import com.lingoarena.dto.response.GameResultResponse;
import com.lingoarena.dto.response.StatsResponse;
import com.lingoarena.entity.GameResult;
import com.lingoarena.entity.RoundRecord;
import com.lingoarena.repository.GameResultRepository;
import com.lingoarena.repository.RoundRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 历史记录服务。
 * 提供对战历史查询、单局详情查看、个人统计等功能。
 */
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final GameResultRepository gameResultRepository;
    private final RoundRecordRepository roundRecordRepository;

    /** 获取用户的对战历史列表（分页，按时间倒序） */
    public Page<GameResultResponse> getRoomHistory(Long userId, Pageable pageable) {
        Page<GameResult> results = gameResultRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable);

        return results.map(this::toGameResultResponse);
    }

    /** 获取单局详情（包含每轮答题记录） */
    public GameResultResponse getRoomDetail(Long roomId, Long userId) {
        List<RoundRecord> rounds = roundRecordRepository.findByRoomId(roomId);

        return GameResultResponse.builder()
                .roomId(roomId)
                .rounds(rounds.stream()
                        .map(r -> GameResultResponse.RoundInfo.builder()
                                .roundNumber(r.getRoundNumber())
                                .questionType(r.getQuestionType().name())
                                .userAnswer(r.getUserAnswer())
                                .isCorrect(r.getIsCorrect())
                                .timeSpentMs(r.getTimeSpentMs())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    /** 获取用户个人统计（总局数、胜/负、平均分） */
    public StatsResponse getStats(Long userId) {
        List<GameResult> results = gameResultRepository.findByUserId(userId);

        long totalGames = results.size();
        long wins = results.stream().filter(r -> r.getScore() > 0).count();
        long losses = totalGames - wins;
        double avgScore = results.stream()
                .mapToInt(GameResult::getScore)
                .average()
                .orElse(0.0);

        return StatsResponse.builder()
                .totalGames(totalGames)
                .wins(wins)
                .losses(losses)
                .avgScore(Math.round(avgScore * 10.0) / 10.0)
                .build();
    }

    /** 将 GameResult 实体转换为响应 DTO */
    private GameResultResponse toGameResultResponse(GameResult result) {
        return GameResultResponse.builder()
                .id(result.getId())
                .roomId(result.getRoom().getId())
                .userId(result.getUser().getId())
                .score(result.getScore())
                .correctCount(result.getCorrectCount())
                .wrongCount(result.getWrongCount())
                .totalQuestions(result.getTotalQuestions())
                .createdAt(result.getCreatedAt())
                .build();
    }
}
