package com.chatdiet.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The durable session/message log behind CHAT_SESSION and CHAT_MESSAGE - separate from
 * ConversationHistoryStore's short in-memory window that's actually replayed to the model.
 */
@Service
public class ChatSessionService {

    /** Rough chars-per-token approximation - fine for a soft usage nudge, not billing accuracy. */
    private static final int CHARS_PER_TOKEN_ESTIMATE = 4;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Value("${chat-diet.chat.token-warning-threshold:6000}")
    private int tokenWarningThreshold;

    public ChatSessionService(ChatSessionRepository chatSessionRepository,
                               ChatMessageRepository chatMessageRepository) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * Persists both halves of the turn, updates the session's token estimate, and appends a
     * one-time size warning to the reply once the configured threshold is crossed.
     */
    public String recordTurn(String sessionKey, String userText, String assistantReply) {
        var session = chatSessionRepository.findBySessionKey(sessionKey)
                .orElseGet(() -> chatSessionRepository.save(new ChatSession(sessionKey)));

        chatMessageRepository.save(new ChatMessage(sessionKey, "user", userText));
        chatMessageRepository.save(new ChatMessage(sessionKey, "assistant", assistantReply));

        var addedTokens = estimateTokens(userText) + estimateTokens(assistantReply);
        var updated = session.withAddedTokens(addedTokens);

        var shouldWarn = !Boolean.TRUE.equals(updated.sizeWarned())
                && updated.tokenEstimate() != null
                && updated.tokenEstimate() >= tokenWarningThreshold;

        chatSessionRepository.save(shouldWarn ? updated.warned() : updated);

        return shouldWarn
                ? assistantReply + "\n\n(This conversation is getting long - consider starting a New Session.)"
                : assistantReply;
    }

    private int estimateTokens(String text) {
        return text == null ? 0 : Math.max(1, text.length() / CHARS_PER_TOKEN_ESTIMATE);
    }
}
