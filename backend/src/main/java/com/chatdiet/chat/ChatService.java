package com.chatdiet.chat;

import com.chatdiet.note.SaveNoteTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private static final String SYSTEM_PROMPT = """
            You are the backend of a personal diet and health tracking app.
            The only capability you have right now is saving freeform notes
            with the save_note tool. You are not a nanny: never volunteer
            commentary, just do what is asked. Any value you save, read back
            to the user so they can catch transcription errors.
            """;

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder chatClientBuilder, SaveNoteTool saveNoteTool) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(saveNoteTool)
                .build();
    }

    public String reply(String userText) {
        return chatClient.prompt()
                .user(userText)
                .call()
                .content();
    }
}
