package com.chatdiet.chat;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory only - short-lived conversational context, not the durable CHAT_SESSION/CHAT_MESSAGE
 * history SPEC describes. That needs its own persistence, "New Session" UI, and token-size
 * warnings; this just gives the model enough of the last few turns to do things like resolve a
 * photo-analysis clarifying question without re-sending the photo.
 */
@Component
public class ConversationHistoryStore {

    public static final String DEFAULT_SESSION = "default";
    private static final int MAX_MESSAGES = 20;

    private final Map<String, Deque<Message>> bySession = new ConcurrentHashMap<>();

    /** Returns an immutable snapshot of the recent messages for a session, oldest first. */
    public List<Message> get(String sessionId) {
        return List.copyOf(bySession.getOrDefault(sessionId, new ArrayDeque<>()));
    }

    /** Appends messages to a session's history, evicting the oldest once {@link #MAX_MESSAGES} is exceeded. */
    public synchronized void append(String sessionId, Message... messages) {
        var deque = bySession.computeIfAbsent(sessionId, key -> new ArrayDeque<>());
        for (var message : messages) {
            deque.addLast(message);
            while (deque.size() > MAX_MESSAGES) {
                deque.removeFirst();
            }
        }
    }

    /** Drops all in-memory history for every session (e.g. for test isolation). */
    public void clearAll() {
        bySession.clear();
    }
}
