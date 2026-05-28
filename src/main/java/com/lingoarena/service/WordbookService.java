package com.lingoarena.service;

import com.lingoarena.entity.Word;
import com.lingoarena.entity.Wordbook;
import com.lingoarena.exception.BusinessException;
import com.lingoarena.exception.ErrorCode;
import com.lingoarena.repository.WordRepository;
import com.lingoarena.repository.WordbookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 词库服务。
 * 提供词库查询、单词列表查询等功能。
 */
@Service
@RequiredArgsConstructor
public class WordbookService {

    private final WordbookRepository wordbookRepository;
    private final WordRepository wordRepository;

    /** 获取所有启用的词库列表 */
    public List<Wordbook> getActiveWordbooks() {
        return wordbookRepository.findByIsActiveTrue();
    }

    /** 获取单个词库详情 */
    public Wordbook getWordbook(Long id) {
        return wordbookRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORDBOOK_NOT_FOUND.getCode(),
                        ErrorCode.WORDBOOK_NOT_FOUND.getMessage()));
    }

    /** 分页查询词库中的单词 */
    public Page<Word> getWords(Long wordbookId, Pageable pageable) {
        if (!wordbookRepository.existsById(wordbookId)) {
            throw new BusinessException(ErrorCode.WORDBOOK_NOT_FOUND.getCode(),
                    ErrorCode.WORDBOOK_NOT_FOUND.getMessage());
        }
        return wordRepository.findByWordbookId(wordbookId, pageable);
    }

    /** 获取词库中所有单词（用于游戏初始化时生成题目） */
    public List<Word> getAllWords(Long wordbookId) {
        if (!wordbookRepository.existsById(wordbookId)) {
            throw new BusinessException(ErrorCode.WORDBOOK_NOT_FOUND.getCode(),
                    ErrorCode.WORDBOOK_NOT_FOUND.getMessage());
        }
        return wordRepository.findByWordbookId(wordbookId);
    }
}
