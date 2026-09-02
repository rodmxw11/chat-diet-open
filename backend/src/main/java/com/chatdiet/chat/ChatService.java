package com.chatdiet.chat;

import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.food.FoodLogClaimDetector;
import com.chatdiet.food.FoodLogVerificationContext;
import com.chatdiet.intent.PromptAssembler;
import com.chatdiet.sql.SqlUsageContext;
import com.chatdiet.weight.WeightLogClaimDetector;
import com.chatdiet.weight.WeightLogVerificationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The main chat loop: builds a single {@link ChatClient} at startup with the tool set
 * ({@link PromptAssembler#tools()}), then for each turn sends a freshly-built system prompt
 * ({@link PromptAssembler#systemPrompt()}) - so the model's notion of "now" never goes stale on a
 * long-running process - along with the current metabolic day's recent history from
 * {@link ConversationHistoryStore}, and hands the exchange back to that store to persist and cache,
 * along with this turn's token usage (and the SQL-composer subchat's, if {@code run_sql} ran) for
 * later cost reporting.
 *
 * <p>Also verifies logging claims (food and weight, via {@link #LOG_VERIFIERS}): if the reply looks
 * like it confirms a new log (see {@link FoodLogClaimDetector}/{@link WeightLogClaimDetector}) but
 * the matching verification context shows no logging tool actually ran, it sends one corrective
 * follow-up turn asking the model to either really log it or admit nothing was saved, rather than
 * silently persisting a hallucinated confirmation. If the retry's reply still looks like the same
 * kind of unverified claim, the model isn't trusted to self-report the failure either - the reply
 * sent to the user is replaced with a plain, honest, code-generated message instead of whatever the
 * model said, since by that point it's already shown it isn't reliably reporting its own tool-call
 * outcome.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** One kind of "did the model actually save what it just claimed to save" check. */
    private record LogVerifier(String kind, Predicate<String> looksLikeClaim, Supplier<Boolean> wasLogged,
                                String nudge, String stillUnverifiedMessage) {
    }

    private static final String FOOD_NUDGE = """
            [System check: that reply described logging food, but no logging tool call actually \
            went through, so nothing was saved. If food was genuinely described, call log_food, \
            log_food_by_upc, or log_cached_food now with your best estimate before replying again. \
            If nothing should have been logged (e.g. you were reporting past data, not a new \
            entry), say so plainly instead.]""";

    private static final String FOOD_STILL_UNVERIFIED_MESSAGE =
            "That didn't actually save - I tried twice and the food log still didn't go through. "
                    + "Please resend it and I'll try again.";

    private static final String WEIGHT_NUDGE = """
            [System check: that reply described logging a weight entry, but log_weight never \
            actually ran, so nothing was saved. If a weight reading was genuinely given, call \
            log_weight now before replying again. If nothing should have been logged (e.g. you were \
            reporting a past or projected weight, not a new entry), say so plainly instead.]""";

    private static final String WEIGHT_STILL_UNVERIFIED_MESSAGE =
            "That didn't actually save - I tried twice and the weight log still didn't go through. "
                    + "Please resend it and I'll try again.";

    private final ChatClient chatClient;
    private final PromptAssembler promptAssembler;
    private final ConversationHistoryStore historyStore;
    private final DayBoundaryService dayBoundaryService;
    private final SqlUsageContext sqlUsageContext;
    private final List<LogVerifier> logVerifiers;

    public ChatService(ChatClient.Builder chatClientBuilder, PromptAssembler promptAssembler,
                        ConversationHistoryStore historyStore, DayBoundaryService dayBoundaryService,
                        SqlUsageContext sqlUsageContext, FoodLogVerificationContext foodLogVerificationContext,
                        WeightLogVerificationContext weightLogVerificationContext) {
        this.chatClient = chatClientBuilder
                .defaultTools(promptAssembler.tools().toArray())
                .build();
        this.promptAssembler = promptAssembler;
        this.historyStore = historyStore;
        this.dayBoundaryService = dayBoundaryService;
        this.sqlUsageContext = sqlUsageContext;
        this.logVerifiers = List.of(
                new LogVerifier("food", FoodLogClaimDetector::looksLikeFoodLogClaim,
                        foodLogVerificationContext::wasLogged, FOOD_NUDGE, FOOD_STILL_UNVERIFIED_MESSAGE),
                new LogVerifier("weight", WeightLogClaimDetector::looksLikeWeightLogClaim,
                        weightLogVerificationContext::wasLogged, WEIGHT_NUDGE, WEIGHT_STILL_UNVERIFIED_MESSAGE));
    }

    /** Replies as of now, within the current metabolic day. See {@link #reply}. */
    public String reply(String userText) {
        var now = LocalDateTime.now();
        return reply(dayBoundaryService.metabolicDateOf(now), now, userText);
    }

    /**
     * Runs one chat turn: sends {@code userText} to the model with the day's recent history and
     * records both halves of the exchange against that day.
     *
     * @param occurredAt when the message was composed, which for an offline-queued message is
     *                   earlier than now and is what {@code metabolicDate} was derived from
     */
    public String reply(LocalDate metabolicDate, LocalDateTime occurredAt, String userText) {
        var history = historyStore.get(metabolicDate);
        var chatResponse = chatClient.prompt()
                .system(promptAssembler.systemPrompt())
                .messages(history)
                .user(userText)
                .call()
                .chatResponse();

        var content = chatResponse.getResult().getOutput().getText();
        var chatUsage = TokenUsage.from(chatResponse.getMetadata().getUsage());

        // At most one corrective retry per turn - the first unverified claim found (food checked
        // before weight) gets the nudge; a turn narrating two different kinds of unverified claims
        // at once hasn't been observed in practice.
        for (var verifier : logVerifiers) {
            if (!verifier.looksLikeClaim().test(content) || verifier.wasLogged().get()) {
                continue;
            }
            log.warn("Reply looked like a {}-log confirmation but no logging tool ran; retrying with a "
                    + "corrective nudge. Unverified reply: {}", verifier.kind(), content);

            var retryMessages = new ArrayList<Message>(history);
            retryMessages.add(new UserMessage(userText));
            retryMessages.add(new AssistantMessage(content));
            retryMessages.add(new UserMessage(verifier.nudge()));

            var retryResponse = chatClient.prompt()
                    .system(promptAssembler.systemPrompt())
                    .messages(retryMessages)
                    .call()
                    .chatResponse();

            content = retryResponse.getResult().getOutput().getText();
            chatUsage = chatUsage.plus(TokenUsage.from(retryResponse.getMetadata().getUsage()));

            log.warn("Corrective retry {}. New reply: {}",
                    verifier.wasLogged().get() ? "logged the " + verifier.kind() : "still did not log anything",
                    content);

            if (verifier.looksLikeClaim().test(content) && !verifier.wasLogged().get()) {
                log.warn("Retry still looked unverified after the nudge - not trusting the model's self-report; "
                        + "telling the user directly instead. Model's retry reply was: {}", content);
                content = verifier.stillUnverifiedMessage();
            }
            break;
        }

        historyStore.append(metabolicDate, occurredAt, userText, content, chatUsage, sqlUsageContext.usage().orElse(null));
        return content;
    }
}
