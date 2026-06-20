package com.lingoarena.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingoarena.entity.Word;
import com.lingoarena.entity.Wordbook;
import com.lingoarena.enums.WordbookLevel;
import com.lingoarena.repository.WordRepository;
import com.lingoarena.repository.WordbookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 应用启动时从 classpath JSON 导入单词（幂等：仅当词库尚无单词时导入）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WordbookDataImporter implements ApplicationListener<ApplicationReadyEvent> {

    /** 回合制默认 10 轮需要 20 题，种子数据至少满足此数量 */
    private static final int MIN_WORDS_PER_WORDBOOK = 20;

    private static final Map<WordbookLevel, String> DATA_FILES = Map.of(
            WordbookLevel.CET4, "data/cet4.json",
            WordbookLevel.CET6, "data/cet6.json",
            WordbookLevel.KAOYAN, "data/kaoyan.json"
    );

    private final WordbookRepository wordbookRepository;
    private final WordRepository wordRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void onApplicationEvent(ApplicationReadyEvent event) {
        for (Map.Entry<WordbookLevel, String> entry : DATA_FILES.entrySet()) {
            importIfEmpty(entry.getKey(), entry.getValue());
        }
    }

    private void importIfEmpty(WordbookLevel level, String resourcePath) {
        Optional<Wordbook> wordbookOpt = wordbookRepository.findByLevel(level);
        if (wordbookOpt.isEmpty()) {
            log.warn("Wordbook level {} not found, skip import from {}", level, resourcePath);
            return;
        }

        Wordbook wordbook = wordbookOpt.get();
        long existing = wordRepository.countByWordbookId(wordbook.getId());
        if (existing >= MIN_WORDS_PER_WORDBOOK) {
            log.debug("Wordbook {} has {} words, skip import", level, existing);
            return;
        }

        List<WordEntry> entries = loadEntries(resourcePath);
        if (entries.isEmpty()) {
            log.warn("No word entries in {}, skip import for {}", resourcePath, level);
            return;
        }

        if (existing > 0) {
            log.info("Wordbook {} has only {} words (< {}), reimporting from {}",
                    level, existing, MIN_WORDS_PER_WORDBOOK, resourcePath);
            wordRepository.deleteByWordbookId(wordbook.getId());
        }

        for (int i = 0; i < entries.size(); i++) {
            WordEntry entry = entries.get(i);
            wordRepository.save(Word.builder()
                    .wordbook(wordbook)
                    .english(entry.english())
                    .chinese(entry.chinese())
                    .phonetic(entry.phonetic())
                    .orderIndex(i + 1)
                    .build());
        }

        log.info("Imported {} words into wordbook {} ({})", entries.size(), wordbook.getId(), level);
    }

    private List<WordEntry> loadEntries(String resourcePath) {
        try (InputStream in = new ClassPathResource(resourcePath).getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to load word data from {}", resourcePath, e);
            return List.of();
        }
    }

    private record WordEntry(String english, String chinese, String phonetic) {}
}
