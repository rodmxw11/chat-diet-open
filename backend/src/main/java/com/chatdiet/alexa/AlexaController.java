package com.chatdiet.alexa;

import com.amazon.ask.model.Intent;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.LaunchRequest;
import com.amazon.ask.model.Request;
import com.amazon.ask.model.RequestEnvelope;
import com.amazon.ask.model.SessionEndedRequest;
import com.amazon.ask.model.Slot;
import com.amazon.ask.util.JacksonSerializer;
import com.chatdiet.chat.ChatService;
import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.food.FastFoodLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Endpoint Alexa's skill service calls for every request, after {@link AlexaSignatureFilter} has
 * verified it. Turns the three logging intents into the same chat turn the web app would send -
 * carrier phrase + what was said, deterministic fast path first, then {@link ChatService} on the
 * voice channel - and speaks the reply. Bare answers to the model's clarifying questions
 * ({@code ChoiceIntent}, yes/no) go through the same turn with no carrier phrase: the logging
 * intents' {@code AMAZON.SearchQuery} slots need a carrier phrase, so "medium" alone can't reach them.
 *
 * <p>An Alexa session is only mic-open/mic-closed state: history is keyed by metabolic day, so a
 * voice turn lands in the same conversation as that day's typed turns, and ending a session writes
 * or clears nothing. Logging turns always keep the session open with a reprompt, since the reply
 * may be a clarifying question and there's no structural signal to tell. Any failure closes the
 * session instead, so a broken turn can't invite a second bad write.
 *
 * <p>{@code tailscale funnel --set-path=/alexa} strips the mount-point prefix by default, so the
 * Funnel command must target {@code http://localhost:8081/alexa} (path included), not bare
 * {@code http://localhost:8081} - otherwise the backend receives requests at {@code /}, which
 * collides with Spring's static-resource handling for the PWA's own {@code GET /}.
 */
@RestController
public class AlexaController {

    private static final Logger log = LoggerFactory.getLogger(AlexaController.class);

    private static final String TEXT_SLOT = "text";
    private static final String REPROMPT = "Anything else?";
    private static final String ANSWER_REPROMPT = "Say your answer, or stop.";
    private static final String NOT_UNDERSTOOD = "Sorry?";
    private static final String HELP = "Say I ate, then the food. Or say I weigh, or note that.";
    private static final String FAILED =
            "Sorry, something went wrong. Check the app before trying that again.";

    /** Carrier phrases matching the web app's quick-entry buttons, per logging intent. */
    private static final Map<String, String> CARRIER_PHRASES = Map.of(
            "AteIntent", "I ate ",
            "NoteIntent", "Note that ",
            "WeightIntent", "Weight ");

    private static final Pattern REF_MARKER = Pattern.compile("\\[ref:[^\\]]*\\]");
    private static final Pattern MARKDOWN = Pattern.compile("[*_#`>]+");
    private static final Pattern THOUSANDS_COMMA = Pattern.compile("(?<=\\d),(?=\\d{3}\\b)");
    private static final Pattern STANDALONE_INTEGER = Pattern.compile("(?<![\\d.])\\d+(?!\\.?\\d)");

    private final FastFoodLogService fastFoodLogService;
    private final ChatService chatService;
    private final DayBoundaryService dayBoundaryService;
    private final JacksonSerializer serializer = new JacksonSerializer();

    public AlexaController(FastFoodLogService fastFoodLogService, ChatService chatService,
                           DayBoundaryService dayBoundaryService) {
        this.fastFoodLogService = fastFoodLogService;
        this.chatService = chatService;
        this.dayBoundaryService = dayBoundaryService;
    }

    @PostMapping("/alexa")
    public Map<String, Object> handle(@RequestBody String requestBody) {
        var request = serializer.deserialize(requestBody, RequestEnvelope.class).getRequest();

        if (request instanceof LaunchRequest) {
            return speak("Ready.", false);
        }
        if (request instanceof SessionEndedRequest ended) {
            log.info("Alexa session ended: {}", ended.getReason());
            return Map.of("version", "1.0", "response", Map.of());
        }
        if (request instanceof IntentRequest intentRequest) {
            return handleIntent(intentRequest);
        }
        log.warn("Unhandled Alexa request type: {}", request.getType());
        return speak(NOT_UNDERSTOOD, false);
    }

    private Map<String, Object> handleIntent(IntentRequest request) {
        var intentName = request.getIntent().getName();
        switch (intentName) {
            case "AMAZON.StopIntent", "AMAZON.CancelIntent" -> {
                return speak("Okay.", true);
            }
            case "AMAZON.HelpIntent" -> {
                return speak(HELP, false);
            }
            default -> {
                // AMAZON.FallbackIntent and anything unexpected: say so and write nothing.
            }
        }

        var text = turnText(request.getIntent());
        if (text == null) {
            log.info("Alexa {} not understood - nothing written", intentName);
            return speak(NOT_UNDERSTOOD, false);
        }

        var started = System.currentTimeMillis();
        try {
            var reply = logTurn(text, request);
            log.info("Alexa {} turn took {} ms", intentName, System.currentTimeMillis() - started);
            return speak(reply, false);
        } catch (RuntimeException e) {
            log.error("Alexa {} turn failed after {} ms", intentName, System.currentTimeMillis() - started, e);
            return speak(FAILED, true);
        }
    }

    /**
     * Runs one turn exactly as the web chat does - fast path first, then the model - filed under
     * the metabolic day of Alexa's own request timestamp.
     */
    private String logTurn(String text, Request request) {
        var sentAt = request.getTimestamp() == null ? null : request.getTimestamp().toInstant().toString();
        var occurredAt = dayBoundaryService.occurredAt(sentAt);
        var metabolicDate = dayBoundaryService.metabolicDateOf(occurredAt);
        return fastFoodLogService.tryHandle(metabolicDate, occurredAt, text)
                .orElseGet(() -> chatService.reply(metabolicDate, occurredAt, text, true));
    }

    /**
     * The chat text for an intent: a logging intent's carrier phrase plus what was said, or - for
     * a bare answer to the model's own clarifying question ("medium", "two", "150 grams", "yes") -
     * just the answer, which the model resolves against that question since it sees the day's
     * whole conversation. Null when there's nothing usable, so nothing gets written.
     */
    private static String turnText(Intent intent) {
        var carrier = CARRIER_PHRASES.get(intent.getName());
        if (carrier != null) {
            var spoken = slotValue(intent, TEXT_SLOT);
            return spoken == null ? null : carrier + spoken;
        }
        return switch (intent.getName()) {
            case "ChoiceIntent" -> choiceText(intent);
            case "AMAZON.YesIntent" -> "yes";
            case "AMAZON.NoIntent" -> "no";
            default -> null;
        };
    }

    /** "the second one" → "2", "150 grams" → "150 grams", "two" → "2", "medium" → "medium". */
    private static String choiceText(Intent intent) {
        var ordinal = slotValue(intent, "ordinal");
        if (ordinal != null) {
            return ordinal;
        }
        var number = slotValue(intent, "number");
        if (number != null) {
            var unit = slotValue(intent, "unit");
            return unit == null ? number : number + " " + unit;
        }
        return slotValue(intent, "choice");
    }

    private static String slotValue(Intent intent, String slotName) {
        var slots = intent.getSlots();
        Slot slot = slots == null ? null : slots.get(slotName);
        var value = slot == null ? null : slot.getValue();
        return value == null || value.isBlank() || "?".equals(value) ? null : value.strip();
    }

    /**
     * Builds a response envelope. Open sessions get a reprompt - one that invites an answer when
     * the reply is itself a question, since "Anything else?" would talk over it.
     */
    private static Map<String, Object> speak(String text, boolean endSession) {
        var response = new LinkedHashMap<String, Object>();
        response.put("outputSpeech", ssml(text));
        if (!endSession) {
            var reprompt = text.strip().endsWith("?") ? ANSWER_REPROMPT : REPROMPT;
            response.put("reprompt", Map.of("outputSpeech", ssml(reprompt)));
        }
        response.put("shouldEndSession", endSession);
        return Map.of("version", "1.0", "response", response);
    }

    private static Map<String, String> ssml(String text) {
        return Map.of("type", "SSML", "ssml", "<speak>" + toSsmlBody(text) + "</speak>");
    }

    /**
     * Makes a model reply safe and natural to speak: drops any leftover clarification markers and
     * markdown, escapes XML, and reads whole numbers as cardinals ("234" as "two hundred
     * thirty-four", not digit by digit). Decimals are left to Alexa's default reading.
     */
    static String toSsmlBody(String text) {
        var cleaned = MARKDOWN.matcher(REF_MARKER.matcher(text).replaceAll("")).replaceAll("");
        cleaned = THOUSANDS_COMMA.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replaceAll("\\s+", " ").strip();
        return STANDALONE_INTEGER.matcher(cleaned)
                .replaceAll(m -> "<say-as interpret-as=\"cardinal\">" + m.group() + "</say-as>");
    }
}
