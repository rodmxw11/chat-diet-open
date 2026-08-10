package com.chatdiet.chat;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.time.LocalDate;
import java.util.List;

/** Spring Data JDBC repository for the durable {@link ChatMessage} log. */
public interface ChatMessageRepository extends ListCrudRepository<ChatMessage, Long> {

    /**
     * A whole day's conversation, oldest first, for display. The {@code id} tiebreaker matters:
     * both halves of one turn can share a {@code created_at}. Capped defensively - a heavy day
     * is dozens of messages, not hundreds.
     */
    @Query("SELECT * FROM chat_message WHERE metabolic_date = :metabolicDate ORDER BY created_at, id LIMIT 200")
    List<ChatMessage> findByMetabolicDate(LocalDate metabolicDate);

    /**
     * The most recent messages of a day, newest first - used to rehydrate the model's context
     * window after a restart. Callers reverse the result to get chronological order.
     */
    @Query("SELECT * FROM chat_message WHERE metabolic_date = :metabolicDate ORDER BY created_at DESC, id DESC LIMIT :limit")
    List<ChatMessage> findRecentByMetabolicDate(LocalDate metabolicDate, int limit);
}
