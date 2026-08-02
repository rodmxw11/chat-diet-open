package com.chatdiet.chat;

/**
 * Request body for a plain-text chat turn.
 *
 * @param sessionId    conversation key; when null the caller falls back to
 *                      {@link ConversationHistoryStore#DEFAULT_SESSION}
 * @param clientSentAt when the user actually hit send, not when the request reaches the server -
 *                      they can differ when the service worker queued this while offline and
 *                      replayed it later. Kept as the raw ISO string the browser sent; only ever
 *                      surfaced to the model as context, never parsed/trusted server-side.
 */
public record ChatRequest(String text, String sessionId, String clientSentAt) {
}
