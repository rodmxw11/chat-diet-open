package com.chatdiet.chat;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the conversation history for each metabolic day - both the durable CHAT_MESSAGE log and
 * the short in-memory window actually replayed to the model.
 *
 * <p>There is no session concept: the metabolic day is the conversation, so every device and
 * channel writing on the same day appends to the same thread.
 *
 * <p>The in-memory window is a cache, not the source of truth. It is rebuilt from the database
 * on first access of a day, which matters because the UI can display a whole day's transcript
 * ({@link #messagesFor}) - without rehydration a backend restart would leave the model with
 * amnesia about a conversation the user can plainly still see on screen.
 */
@Component
public class ConversationHistoryStore {

    private final ChatMessageRepository chatMessageRepository;

    /**
     * How many messages of the current day are replayed to the model. The DB is the real memory
     * - recall of earlier facts happens through DB-reading tools, not by holding a long
     * transcript in context - so this stays small deliberately. Independent of what the UI
     * shows: {@link #messagesFor} always returns the whole day.
     */
    private final int maxContextMessages;

    private final Map<LocalDate, Deque<Message>> byDate = new ConcurrentHashMap<>();

    public ConversationHistoryStore(ChatMessageRepository chatMessageRepository,
                                     @Value("${chat-diet.chat.context-messages:10}") int maxContextMessages) {
        this.chatMessageRepository = chatMessageRepository;
        this.maxContextMessages = maxContextMessages;
    }

    /**
     * Returns the model-facing context for a day, oldest first, loading it from the database if
     * this day isn't cached yet.
     */
    public synchronized List<Message> get(LocalDate metabolicDate) {
        evictOlderThanYesterday(metabolicDate);
        return List.copyOf(byDate.computeIfAbsent(metabolicDate, this::rehydrate));
    }

    /**
     * Records both halves of a turn with no token usage recorded. See
     * {@link #append(LocalDate, LocalDateTime, String, String, TokenUsage, TokenUsage)}.
     */
    public void append(LocalDate metabolicDate, LocalDateTime occurredAt, String userText, String assistantText) {
        append(metabolicDate, occurredAt, userText, assistantText, null, null);
    }

    /**
     * Records both halves of a turn: persists them to the durable log and adds them to the
     * day's in-memory context window.
     *
     * @param occurredAt when the turn was composed - for an offline-queued message this is
     *                   earlier than now, and it must match the day {@code metabolicDate} was
     *                   derived from
     * @param chatUsage  the main chat model's token usage for this turn, or {@code null}
     * @param opusUsage  the SQL-composer subchat's token usage for this turn (summed if
     *                   {@code run_sql} ran more than once), or {@code null} if it didn't run
     */
    public synchronized void append(LocalDate metabolicDate, LocalDateTime occurredAt,
                                     String userText, String assistantText,
                                     TokenUsage chatUsage, TokenUsage opusUsage) {
        evictOlderThanYesterday(metabolicDate);

        chatMessageRepository.save(new ChatMessage(metabolicDate, "user", userText, occurredAt));
        chatMessageRepository.save(
                new ChatMessage(metabolicDate, "assistant", assistantText, occurredAt, chatUsage, opusUsage));

        var deque = byDate.computeIfAbsent(metabolicDate, this::rehydrate);
        deque.addLast(new UserMessage(userText));
        deque.addLast(new AssistantMessage(assistantText));
        while (deque.size() > maxContextMessages) {
            deque.removeFirst();
        }
    }

    /** A whole day's durable transcript, oldest first, for display. */
    public List<ChatMessage> messagesFor(LocalDate metabolicDate) {
        return chatMessageRepository.findByMetabolicDate(metabolicDate);
    }

    /** Drops all cached history, forcing the next read to reload from the database. */
    public synchronized void clearAll() {
        byDate.clear();
    }

    /** Number of days currently cached. Exposed for tests to verify eviction. */
    synchronized int cachedDayCount() {
        return byDate.size();
    }

    /**
     * Loads the tail of a day's persisted conversation into a fresh context window. Returns an
     * empty deque for a day with no messages, which is still cached so a quiet day doesn't
     * re-query on every turn.
     */
    private Deque<Message> rehydrate(LocalDate metabolicDate) {
        var recent = new ArrayList<>(chatMessageRepository
                .findRecentByMetabolicDate(metabolicDate, maxContextMessages));
        Collections.reverse(recent);

        var deque = new ArrayDeque<Message>();
        for (var message : recent) {
            deque.addLast("assistant".equals(message.role())
                    ? new AssistantMessage(message.content())
                    : new UserMessage(message.content()));
        }
        return deque;
    }

    /**
     * Keeps the cache from growing one entry per day forever. Yesterday is retained because an
     * offline message composed late last night can still arrive today.
     */
    private void evictOlderThanYesterday(LocalDate metabolicDate) {
        byDate.keySet().removeIf(cached -> cached.isBefore(metabolicDate.minusDays(1)));
    }
}
