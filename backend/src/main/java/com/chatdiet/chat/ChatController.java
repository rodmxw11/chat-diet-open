package com.chatdiet.chat;

import com.chatdiet.chart.ChartResultContext;
import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.sql.SqlResultContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * HTTP entry point for the chat feature. Accepts a plain-text turn, routes it through
 * {@link ChatService}, and enriches the reply with any chart series or SQL answer produced as a
 * side effect of the model's tool calls during this request (via the request-scoped
 * {@link ChartResultContext} / {@link SqlResultContext}). Also serves the current metabolic day's
 * transcript so any device can restore it.
 */
@RestController
public class ChatController {

    /** Below this, clock skew/normal round-trip latency isn't worth mentioning to the model. */
    private static final Duration NOTEWORTHY_DELAY = Duration.ofMinutes(2);

    private final ChatService chatService;
    private final ConversationHistoryStore historyStore;
    private final DayBoundaryService dayBoundaryService;
    private final ChartResultContext chartResultContext;
    private final SqlResultContext sqlResultContext;
    private final ChatCostCalculator chatCostCalculator;

    public ChatController(ChatService chatService, ConversationHistoryStore historyStore,
                           DayBoundaryService dayBoundaryService,
                           ChartResultContext chartResultContext, SqlResultContext sqlResultContext,
                           ChatCostCalculator chatCostCalculator) {
        this.chatService = chatService;
        this.historyStore = historyStore;
        this.dayBoundaryService = dayBoundaryService;
        this.chartResultContext = chartResultContext;
        this.sqlResultContext = sqlResultContext;
        this.chatCostCalculator = chatCostCalculator;
    }

    /**
     * Handles a plain-text chat turn: files it under the metabolic day it was composed on, notes
     * any offline-queue timing skew for the model, and returns the reply along with whatever
     * chart/SQL results the turn produced.
     */
    @PostMapping(value = "/api/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse chat(@RequestBody ChatRequest request) {
        var reply = replyAt(request.clientSentAt(), request.text());
        return new ChatResponse(reply, chartResultContext.series().orElse(null),
                sqlResultContext.answer().orElse(null));
    }

    /**
     * Returns a metabolic day's conversation - the current one by default, so a reload or another
     * device can restore it, or an explicit past day for the read-only chat history review page.
     */
    @GetMapping("/api/chat/history")
    public ChatHistoryResponse history(@RequestParam(required = false) LocalDate date) {
        var day = date != null ? date : dayBoundaryService.today();
        var storedMessages = historyStore.messagesFor(day);
        var messages = storedMessages.stream()
                .map(message -> new ChatHistoryResponse.ChatHistoryMessage(
                        message.role(), message.content(), message.createdAt()))
                .toList();
        var cost = chatCostCalculator.costFor(storedMessages);
        return new ChatHistoryResponse(day, messages, cost.haikuCostUsd(), cost.opusCostUsd());
    }

    /**
     * Runs a turn against the metabolic day the message was composed on, rather than the day it
     * arrived - so a message queued offline before midnight stays with the day it belongs to.
     */
    private String replyAt(String clientSentAt, String text) {
        var occurredAt = dayBoundaryService.occurredAt(clientSentAt);
        var metabolicDate = dayBoundaryService.metabolicDateOf(occurredAt);
        return chatService.reply(metabolicDate, occurredAt, withClientTimingNote(text, occurredAt));
    }

    /**
     * Queued-while-offline messages replay well after the user actually sent them. When the gap
     * is noteworthy, tell the model the real composition time so it backdates anything it logs
     * instead of using "now". This is about what the model writes to the food/weight tables; the
     * metabolic day the conversation itself is filed under is handled separately.
     */
    private String withClientTimingNote(String text, LocalDateTime occurredAt) {
        var delay = Duration.between(occurredAt, LocalDateTime.now());
        if (delay.compareTo(NOTEWORTHY_DELAY) <= 0) {
            return text;
        }
        return "[This message was actually composed at " + occurredAt + " (" + delay.toMinutes()
                + " minutes ago) - it was queued offline and just synced. Use that time, not now, "
                + "for anything you log from it.]\n\n" + text;
    }
}
