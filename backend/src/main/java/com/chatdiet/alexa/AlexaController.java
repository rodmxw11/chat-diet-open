package com.chatdiet.alexa;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint Alexa's skill service calls for every request. Stub only for now - a hardcoded
 * response envelope, no signature verification, no {@code ChatService}, no DB - so the
 * connector/path-isolation plumbing ({@link AlexaConnectorConfig},
 * {@link AlexaPathIsolationFilter}) and the Tailscale Funnel routing can be verified end to end
 * before anything that touches real data is wired in.
 *
 * <p>{@code tailscale funnel --set-path=/alexa} strips the mount-point prefix by default, so the
 * Funnel command must target {@code http://localhost:8081/alexa} (path included), not bare
 * {@code http://localhost:8081} - otherwise the backend receives requests at {@code /}, which
 * collides with Spring's static-resource handling for the PWA's own {@code GET /} (confirmed:
 * mapping a controller at {@code /} breaks the PWA homepage with a 405). Keeping the endpoint at
 * {@code /alexa} on both sides avoids that collision entirely.
 */
@RestController
public class AlexaController {

    @PostMapping("/alexa")
    public Map<String, Object> handle(@RequestBody(required = false) String requestBody) {
        return Map.of(
                "version", "1.0",
                "response", Map.of(
                        "outputSpeech", Map.of(
                                "type", "PlainText",
                                "text", "chat-diet Alexa endpoint stub is alive."),
                        "shouldEndSession", true));
    }
}
