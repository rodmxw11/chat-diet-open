package com.chatdiet.sql;

import com.chatdiet.food.FoodEntry;
import com.chatdiet.food.FoodEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReadOnlySqlExecutorTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:file:" + tempDir.resolve("sql-executor-test"));
    }

    @Autowired
    private ReadOnlySqlExecutor readOnlySqlExecutor;

    @Autowired
    private FoodEntryRepository foodEntryRepository;

    @BeforeEach
    void clearAll() {
        foodEntryRepository.deleteAll();
    }

    @Test
    void executesAParameterizedSelectWithDatetimeCoercion() {
        foodEntryRepository.save(new FoodEntry(
                LocalDateTime.of(2026, 7, 1, 12, 0), "lunch", 500, 30.0, 40.0, 15.0, "LOG_FOOD"));
        foodEntryRepository.save(new FoodEntry(
                LocalDateTime.of(2026, 6, 1, 12, 0), "old lunch", 600, 30.0, 40.0, 15.0, "LOG_FOOD"));

        var result = readOnlySqlExecutor.execute(
                "SELECT raw_utterance, total_calories FROM food_entry WHERE logged_at >= ? ORDER BY logged_at",
                List.of(new ParamDef("from", "DATETIME")),
                List.of("2026-06-15T00:00:00"));

        assertThat(result.columns()).contains("RAW_UTTERANCE", "TOTAL_CALORIES");
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0)).contains("lunch", 500);
    }

    @Test
    void rejectsWritesAtTheConnectionLevel() {
        assertThatThrownBy(() -> readOnlySqlExecutor.execute(
                "INSERT INTO food_entry (logged_at, raw_utterance, total_calories, source) "
                        + "VALUES (CURRENT_TIMESTAMP, 'hack', 1, 'LOG_FOOD')",
                List.of(), List.of()))
                .isInstanceOf(SqlExecutionException.class);
    }
}
