package com.chatdiet.digestive;

/**
 * Request DTO for {@link LogDigestiveEventTool}.
 *
 * @param notes optional freeform notes; may be {@code null}
 */
public record LogDigestiveEventRequest(String eventType, String notes) {
}
