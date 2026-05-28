package com.lingoarena.controller;

import com.lingoarena.dto.response.GameResultResponse;
import com.lingoarena.dto.response.StatsResponse;
import com.lingoarena.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 历史记录 + 统计控制器。
 * 注意 @RequestMapping("/api") 是父路径，具体路径在方法上补全。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    /** 获取我的对战历史（分页） */
    @GetMapping("/history/rooms")
    public ResponseEntity<Map<String, Object>> getHistory(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<GameResultResponse> history = historyService.getRoomHistory(userId, PageRequest.of(page, size));
        return ResponseEntity.ok(Map.of(
                "rooms", history.getContent(),
                "total", history.getTotalElements(),
                "page", history.getNumber(),
                "size", history.getSize()
        ));
    }

    /** 获取单局详情 */
    @GetMapping("/history/rooms/{id}")
    public ResponseEntity<GameResultResponse> getDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(historyService.getRoomDetail(id, userId));
    }

    /** 获取个人统计 */
    @GetMapping("/stats/me")
    public ResponseEntity<StatsResponse> getStats(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(historyService.getStats(userId));
    }
}
