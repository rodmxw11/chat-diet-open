package com.chatdiet.chat;

import com.chatdiet.intent.PromptAssembler;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

/**
 * The main chat loop: builds a single {@link ChatClient} at startup from the assembled system
 * prompt and tool set ({@link PromptAssembler}), then for each turn replays the short in-memory
 * history from {@link ConversationHistoryStore}, sends the new user message, appends the
 * exchange back to that store, and hands the reply to {@link ChatSessionService} to persist
 * durably and possibly append a session-size warning.
 */
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final ConversationHistoryStore historyStore;
    private final ChatSessionService chatSessionService;

    public ChatService(ChatClient.Builder chatClientBuilder, PromptAssembler promptAssembler,
                        ConversationHistoryStore historyStore, ChatSessionService chatSessionService) {
        var assembled = promptAssembler.assemble();
        this.chatClient = chatClientBuilder
                .defaultSystem(assembled.systemPrompt())
                .defaultTools(assembled.tools().toArray())
                .build();
        this.historyStore = historyStore;
        this.chatSessionService = chatSessionService;
    }

    /** Replies within the default session. See {@link #reply(String, String)}. */
    public String reply(String userText) {
        return reply(ConversationHistoryStore.DEFAULT_SESSION, userText);
    }

    /**
     * Runs one chat turn: sends {@code userText} to the model with the session's recent history,
     * records both halves of the exchange, and returns the reply (possibly with a size warning
     * appended).
     */
    public String reply(String sessionId, String userText) {
        var history = historyStore.get(sessionId);
        var content = chatClient.prompt()
                .messages(history)
                .user(userText)
                .call()
                .content();
        historyStore.append(sessionId, new UserMessage(userText), new AssistantMessage(content));
        return chatSessionService.recordTurn(sessionId, userText, content);
    }
}
