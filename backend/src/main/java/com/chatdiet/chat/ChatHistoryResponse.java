package com.chatdiet.chat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A metabolic day's conversation, returned by {@code GET /api/chat/history} so any device can
 * restore the transcript of the day in progress.
 *
 * <p>Text only: chart series, SQL result tables, and photo attachments are not persisted, so a
 * restored transcript won't show them. That's deliberate - charts and tables are derived views
 * the user can regenerate by asking again, the assistant's prose reply already echoes any
 * numbers it logged, and photo blobs are purged on a schedule so their URLs would dangle.
 */
public record ChatHistoryResponse(LocalDate metabolicDate, List<ChatHistoryMessage> messages) {

    /** One message, shaped for display rather than mirroring the stored row. */
    public record ChatHistoryMessage(String role, String text, LocalDateTime at) {
    }
}
