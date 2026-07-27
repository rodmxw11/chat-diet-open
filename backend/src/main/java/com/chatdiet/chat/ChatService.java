package com.chatdiet.chat;

import com.chatdiet.intent.PromptAssembler;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private final ConversationHistoryStore historyStore;

    public ChatService(ChatClient.Builder chatClientBuilder, PromptAssembler promptAssembler,
                        ConversationHistoryStore historyStore) {
        var assembled = promptAssembler.assemble();
        this.chatClient = chatClientBuilder
                .defaultSystem(assembled.systemPrompt())
                .defaultTools(assembled.tools().toArray())
                .build();
        this.historyStore = historyStore;
    }

    public String reply(String userText) {
        return reply(ConversationHistoryStore.DEFAULT_SESSION, userText);
    }

    public String reply(String sessionId, String userText) {
        var history = historyStore.get(sessionId);
        var content = chatClient.prompt()
                .messages(history)
                .user(userText)
                .call()
                .content();
        historyStore.append(sessionId, new UserMessage(userText), new AssistantMessage(content));
        return content;
    }
}
