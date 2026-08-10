package com.chatdiet.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConversationHistoryStoreTest {

    /** Deliberately not the default of 10, so these also prove the property is wired through. */
    private static final int CONTEXT_MESSAGES = 6;

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 9);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:file:" + tempDir.resolve("history-store-test"));
        registry.add("chat-diet.chat.context-messages", () -> String.valueOf(CONTEXT_MESSAGES));
    }

    @Autowired
    private ConversationHistoryStore historyStore;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @BeforeEach
    void reset() {
        chatMessageRepository.deleteAll();
        historyStore.clearAll();
    }

    @Test
    void persistsBothHalvesOfATurnAgainstTheDay() {
        appendTurn(TODAY, 9, "hi", "hello there");

        assertThat(historyStore.messagesFor(TODAY))
                .extracting(ChatMessage::role, ChatMessage::content)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("user", "hi"),
                        org.assertj.core.groups.Tuple.tuple("assistant", "hello there"));
    }

    @Test
    void contextWindowKeepsOnlyTheMostRecentMessages() {
        for (int i = 0; i < 8; i++) {
            appendTurn(TODAY, 9, "question " + i, "answer " + i);
        }

        var context = historyStore.get(TODAY);

        assertThat(context)
                .as("window honours the configured size, not the built-in default")
                .hasSize(CONTEXT_MESSAGES);
        assertThat(textOf(context))
                .as("oldest turns are evicted, newest retained")
                .doesNotContain("question 0", "question 4")
                .contains("question 7", "answer 7");
    }

    @Test
    void daysAreSeparateConversations() {
        appendTurn(YESTERDAY, 22, "late night snack", "logged");
        appendTurn(TODAY, 9, "breakfast", "logged");

        assertThat(historyStore.messagesFor(YESTERDAY))
                .extracting(ChatMessage::content)
                .containsExactly("late night snack", "logged");
        assertThat(textOf(historyStore.get(TODAY))).doesNotContain("late night snack");
    }

    /** The restart case: memory is gone but the day's conversation must come back from the DB. */
    @Test
    void contextIsRehydratedFromTheDatabase() {
        appendTurn(TODAY, 9, "I ate a banana", "Logged, 105 calories.");
        historyStore.clearAll();

        assertThat(textOf(historyStore.get(TODAY))).contains("I ate a banana", "Logged, 105 calories.");
    }

    @Test
    void rehydrationRespectsTheWindowAndReturnsChronologicalOrder() {
        for (int i = 0; i < 8; i++) {
            appendTurn(TODAY, 9, "question " + i, "answer " + i);
        }
        historyStore.clearAll();

        var context = historyStore.get(TODAY);

        assertThat(context).hasSize(CONTEXT_MESSAGES);
        var texts = textOf(context);
        assertThat(texts).doesNotContain("question 0").contains("question 5");
        assertThat(texts.indexOf("question 7"))
                .as("oldest first, so the last turn's question precedes its answer")
                .isLessThan(texts.indexOf("answer 7"))
                .isGreaterThan(texts.indexOf("question 5"));
    }

    @Test
    void staleDaysAreEvictedSoTheCacheDoesNotGrowForever() {
        historyStore.get(TODAY.minusDays(5));
        historyStore.get(YESTERDAY);
        historyStore.get(TODAY);

        assertThat(historyStore.cachedDayCount())
                .as("only today and yesterday are kept; yesterday still matters for offline replay")
                .isEqualTo(2);
    }

    private void appendTurn(LocalDate date, int hour, String userText, String assistantText) {
        historyStore.append(date, date.atTime(hour, 0), userText, assistantText);
    }

    private static String textOf(List<Message> messages) {
        return messages.stream().map(Message::getText).reduce("", (a, b) -> a + "\n" + b);
    }
}
