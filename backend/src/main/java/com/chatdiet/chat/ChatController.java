package com.chatdiet.chat;

import com.chatdiet.photo.PhotoContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

@RestController
public class ChatController {

    private final ChatService chatService;
    private final PhotoContext photoContext;

    public ChatController(ChatService chatService, PhotoContext photoContext) {
        this.chatService = chatService;
        this.photoContext = photoContext;
    }

    @PostMapping(value = "/api/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse chat(@RequestBody ChatRequest request) {
        var sessionId = request.sessionId() != null ? request.sessionId() : ConversationHistoryStore.DEFAULT_SESSION;
        return new ChatResponse(chatService.reply(sessionId, request.text()));
    }

    @PostMapping(value = "/api/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChatResponse chatWithPhoto(@RequestParam("text") String text,
                                       @RequestParam(value = "sessionId", required = false) String sessionId,
                                       @RequestParam("photo") MultipartFile photo) {
        try {
            photoContext.setPhotoBytes(photo.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        var effectiveSession = sessionId != null ? sessionId : ConversationHistoryStore.DEFAULT_SESSION;
        var textWithPhotoNote = text + "\n\n[A photo is attached to this message - use analyze_food_photo.]";
        return new ChatResponse(chatService.reply(effectiveSession, textWithPhotoNote));
    }
}
