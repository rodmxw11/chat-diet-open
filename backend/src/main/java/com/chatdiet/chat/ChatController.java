package com.chatdiet.chat;

import com.chatdiet.chart.ChartResultContext;
import com.chatdiet.photo.PhotoContext;
import com.chatdiet.sql.SqlResultContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;

@RestController
public class ChatController {

    /** Below this, clock skew/normal round-trip latency isn't worth mentioning to the model. */
    private static final Duration NOTEWORTHY_DELAY = Duration.ofMinutes(2);

    private final ChatService chatService;
    private final PhotoContext photoContext;
    private final ChartResultContext chartResultContext;
    private final SqlResultContext sqlResultContext;

    public ChatController(ChatService chatService, PhotoContext photoContext, ChartResultContext chartResultContext,
                           SqlResultContext sqlResultContext) {
        this.chatService = chatService;
        this.photoContext = photoContext;
        this.chartResultContext = chartResultContext;
        this.sqlResultContext = sqlResultContext;
    }

    @PostMapping(value = "/api/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse chat(@RequestBody ChatRequest request) {
        var sessionId = request.sessionId() != null ? request.sessionId() : ConversationHistoryStore.DEFAULT_SESSION;
        var text = withClientTimingNote(request.text(), request.clientSentAt());
        var reply = chatService.reply(sessionId, text);
        return new ChatResponse(reply, chartResultContext.series().orElse(null), sqlResultContext.answer().orElse(null));
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
        var reply = chatService.reply(effectiveSession, textWithPhotoNote);
        return new ChatResponse(reply, chartResultContext.series().orElse(null), sqlResultContext.answer().orElse(null));
    }

    /**
     * Queued-while-offline messages replay well after the user actually sent them. When the gap
     * is noteworthy, tell the model the real composition time so it backdates anything it logs
     * instead of using "now".
     */
    private String withClientTimingNote(String text, String clientSentAt) {
        if (clientSentAt == null) {
            return text;
        }
        try {
            var sentAt = Instant.parse(clientSentAt);
            var delay = Duration.between(sentAt, Instant.now());
            if (delay.compareTo(NOTEWORTHY_DELAY) > 0) {
                return "[This message was actually composed at " + clientSentAt + " (" + delay.toMinutes()
                        + " minutes ago) - it was queued offline and just synced. Use that time, not now, "
                        + "for anything you log from it.]\n\n" + text;
            }
        } catch (Exception ignored) {
            // Malformed/missing timestamp - not worth failing the request over.
        }
        return text;
    }
}
