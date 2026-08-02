package com.chatdiet.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatSessionServiceTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasourceAndThreshold(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:file:" + tempDir.resolve("chat-session-test"));
        registry.add("chat-diet.chat.token-warning-threshold", () -> "20");
    }

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    private String sessionKey;

    @BeforeEach
    void freshSession() {
        sessionKey = UUID.randomUUID().toString();
    }

    @Test
    void createsASessionAndPersistsBothMessages() {
        chatSessionService.recordTurn(sessionKey, "hi", "hello there");

        var session = chatSessionRepository.findBySessionKey(sessionKey);
        assertThat(session).isPresent();
        assertThat(session.get().tokenEstimate()).isGreaterThan(0);

        var messages = chatMessageRepository.findAll();
        assertThat(messages).extracting(ChatMessage::content).contains("hi", "hello there");
    }

    @Test
    void warnsExactlyOnceAfterCrossingTheThreshold() {
        var longText = "x".repeat(200);

        var firstReply = chatSessionService.recordTurn(sessionKey, longText, longText);
        assertThat(firstReply)
                .as("threshold of 20 tokens should already be crossed by the first long turn")
                .contains("New Session");

        var secondReply = chatSessionService.recordTurn(sessionKey, "more", "more");
        assertThat(secondReply)
                .as("warning should not repeat once size_warned is set")
                .doesNotContain("New Session");
    }
}
