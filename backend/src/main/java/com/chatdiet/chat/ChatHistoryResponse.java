package com.chatdiet.chat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A metabolic day's conversation, returned by {@code GET /api/chat/history} so any device can
 * restore the transcript of the day in progress.
 *
 * <p>Text only: chart series and SQL result tables are not persisted, so a restored transcript
 * won't show them. That's deliberate - they're derived views the user can regenerate by asking
 * again, and the assistant's prose reply already echoes any numbers it logged.
 */
public record ChatHistoryResponse(LocalDate metabolicDate, List<ChatHistoryMessage> messages) {

    /** One message, shaped for display rather than mirroring the stored row. */
    public record ChatHistoryMessage(String role, String text, LocalDateTime at) {
    }
}
