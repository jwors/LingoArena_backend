package com.lingoarena.controller;

import com.lingoarena.entity.Word;
import com.lingoarena.entity.Wordbook;
import com.lingoarena.service.WordbookService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 词库控制器。
 *
 * @PathVariable 从 URL 路径中获取参数，如 /api/wordbooks/1 中的 1
 * @RequestParam 从 URL query 中获取参数，如 ?page=0&size=20
 */
@RestController
@RequestMapping("/api/wordbooks")
@RequiredArgsConstructor
public class WordbookController {

    private final WordbookService wordbookService;

    /** 获取所有可用的词库列表 */
    @GetMapping
    public ResponseEntity<Map<String, List<Wordbook>>> list() {
        return ResponseEntity.ok(Map.of("wordbooks", wordbookService.getActiveWordbooks()));
    }

    /** 获取单个词库详情 */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Wordbook>> get(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("wordbook", wordbookService.getWordbook(id)));
    }

    /** 分页查询词库中的单词 */
    @GetMapping("/{id}/words")
    public ResponseEntity<Map<String, Object>> getWords(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Word> wordPage = wordbookService.getWords(id, PageRequest.of(page, size));
        return ResponseEntity.ok(Map.of(
                "words", wordPage.getContent(),
                "total", wordPage.getTotalElements(),
                "page", wordPage.getNumber(),
                "size", wordPage.getSize()
        ));
    }
}
