package com.chatdiet.chat;

import com.chatdiet.intent.PromptAssembler;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder chatClientBuilder, PromptAssembler promptAssembler) {
        var assembled = promptAssembler.assemble();
        this.chatClient = chatClientBuilder
                .defaultSystem(assembled.systemPrompt())
                .defaultTools(assembled.tools().toArray())
                .build();
    }

    public String reply(String userText) {
        return chatClient.prompt()
                .user(userText)
                .call()
                .content();
    }
}
