package com.chatdiet.chat;

import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.intent.PromptAssembler;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The main chat loop: builds a single {@link ChatClient} at startup with the tool set
 * ({@link PromptAssembler#tools()}), then for each turn sends a freshly-built system prompt
 * ({@link PromptAssembler#systemPrompt()}) - so the model's notion of "now" never goes stale on a
 * long-running process - along with the current metabolic day's recent history from
 * {@link ConversationHistoryStore}, and hands the exchange back to that store to persist and cache.
 */
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final PromptAssembler promptAssembler;
    private final ConversationHistoryStore historyStore;
    private final DayBoundaryService dayBoundaryService;

    public ChatService(ChatClient.Builder chatClientBuilder, PromptAssembler promptAssembler,
                        ConversationHistoryStore historyStore, DayBoundaryService dayBoundaryService) {
        this.chatClient = chatClientBuilder
                .defaultTools(promptAssembler.tools().toArray())
                .build();
        this.promptAssembler = promptAssembler;
        this.historyStore = historyStore;
        this.dayBoundaryService = dayBoundaryService;
    }

    /** Replies as of now, within the current metabolic day. See {@link #reply}. */
    public String reply(String userText) {
        var now = LocalDateTime.now();
        return reply(dayBoundaryService.metabolicDateOf(now), now, userText);
    }

    /**
     * Runs one chat turn: sends {@code userText} to the model with the day's recent history and
     * records both halves of the exchange against that day.
     *
     * @param occurredAt when the message was composed, which for an offline-queued message is
     *                   earlier than now and is what {@code metabolicDate} was derived from
     */
    public String reply(LocalDate metabolicDate, LocalDateTime occurredAt, String userText) {
        var history = historyStore.get(metabolicDate);
        var content = chatClient.prompt()
                .system(promptAssembler.systemPrompt())
                .messages(history)
                .user(userText)
                .call()
                .content();
        historyStore.append(metabolicDate, occurredAt, userText, content);
        return content;
    }
}
