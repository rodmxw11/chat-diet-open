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
