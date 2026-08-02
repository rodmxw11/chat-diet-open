package com.chatdiet.chat;

/**
 * clientSentAt is when the user actually hit send, not when the request reaches the server -
 * they can differ when the service worker queued this while offline and replayed it later. Kept
 * as the raw ISO string the browser sent; only ever surfaced to the model as context, never
 * parsed/trusted server-side.
 */
public record ChatRequest(String text, String sessionId, String clientSentAt) {
}
