package com.chatdiet.chat;

/**
 * Request body for a plain-text chat turn.
 *
 * <p>Deserialization is deliberately lenient about unknown properties: the service worker's
 * offline queue holds requests for up to 24 hours, so after a deploy this has to keep accepting
 * bodies that still carry fields the current version no longer declares (the old {@code
 * sessionId}, for instance). Don't turn on {@code FAIL_ON_UNKNOWN_PROPERTIES}.
 *
 * @param clientSentAt when the user actually hit send, not when the request reaches the server -
 *                      they can differ when the service worker queued this while offline and
 *                      replayed it later. Determines which metabolic day the turn is filed under
 *                      and what timestamp it is stored with, so a message composed just before
 *                      midnight lands in that day even if it syncs the next morning.
 */
public record ChatRequest(String text, String clientSentAt) {
}
