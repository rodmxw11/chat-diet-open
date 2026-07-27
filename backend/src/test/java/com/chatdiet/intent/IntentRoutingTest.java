package com.chatdiet.intent;

import com.chatdiet.chat.ChatService;
import com.chatdiet.note.NoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IntentRoutingTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:file:" + tempDir.resolve("routing-test"));
    }

    @Autowired
    private ChatService chatService;

    @Autowired
    private NoteRepository noteRepository;

    @BeforeEach
    void clearNotes() {
        noteRepository.deleteAll();
    }

    @Test
    void routesNoteTakingUtteranceToSaveNoteTool() {
        chatService.reply("remind myself to buy more almond milk");

        assertThat(noteRepository.findAll())
                .as("save_note should have been invoked and persisted a Note")
                .hasSize(1);
    }

    @Test
    void routesAlternatePhrasingToSaveNoteTool() {
        chatService.reply("note to self: check the mail");

        assertThat(noteRepository.findAll())
                .as("save_note should have been invoked and persisted a Note")
                .hasSize(1);
    }
}
