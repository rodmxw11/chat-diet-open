package com.chatdiet.chat;

import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.intent.PromptAssembler;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The main chat loop: builds a single {@link ChatClient} at startup from the assembled system
 * prompt and tool set ({@link PromptAssembler}), then for each turn replays the current metabolic
 * day's recent history from {@link ConversationHistoryStore}, sends the new user message, and
 * hands the exchange back to that store to persist and cache.
 */
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final ConversationHistoryStore historyStore;
    private final DayBoundaryService dayBoundaryService;

    public ChatService(ChatClient.Builder chatClientBuilder, PromptAssembler promptAssembler,
                        ConversationHistoryStore historyStore, DayBoundaryService dayBoundaryService) {
        var assembled = promptAssembler.assemble();
        this.chatClient = chatClientBuilder
                .defaultSystem(assembled.systemPrompt())
                .defaultTools(assembled.tools().toArray())
                .build();
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
                .messages(history)
                .user(userText)
                .call()
                .content();
        historyStore.append(metabolicDate, occurredAt, userText, content);
        return content;
    }
}
