package com.chatdiet.food;

import com.chatdiet.chat.ConversationHistoryStore;
import com.chatdiet.food.resolve.FastLogParser;
import com.chatdiet.food.resolve.QuantityResolution;
import com.chatdiet.food.resolve.QuantityResolver;
import com.chatdiet.food.resolve.FoodResolver;
import com.chatdiet.fooditem.FoodItem;
import com.chatdiet.fooditem.FoodItemLogger;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Logs a quick "quantity + known food" chat message deterministically, skipping the model
 * entirely - no latency, no token cost, no chance of a narrated-but-unsaved entry. Only fires
 * when {@link FastLogParser} recognizes the shape AND the food phrase is an exact alias hit
 * (deliberately not {@link FoodResolver#resolve}, which would pay an FDC network call on every
 * non-matching message; strong fuzzy singles still go through the model's log_food, which
 * auto-accepts them). Everything else returns empty and the turn proceeds to the model unchanged.
 *
 * <p>The exchange is persisted through {@link ConversationHistoryStore#append}, which also feeds
 * the model's in-memory context window - so a follow-up "make that 100g" correction still works:
 * the model sees the fast-path turn as if it had handled it.
 */
@Service
public class FastFoodLogService {

    private final FoodResolver foodResolver;
    private final QuantityResolver quantityResolver;
    private final FoodItemLogger foodItemLogger;
    private final ConversationHistoryStore historyStore;

    public FastFoodLogService(FoodResolver foodResolver, QuantityResolver quantityResolver,
                               FoodItemLogger foodItemLogger, ConversationHistoryStore historyStore) {
        this.foodResolver = foodResolver;
        this.quantityResolver = quantityResolver;
        this.foodItemLogger = foodItemLogger;
        this.historyStore = historyStore;
    }

    /**
     * Attempts the deterministic fast path for one incoming message.
     *
     * @param occurredAt when the message was composed - an offline-queued "142g cheerios"
     *                   backdates to its composition time, same as the model path
     * @return the fixed confirmation to reply with, or empty to fall through to the model
     */
    public Optional<String> tryHandle(LocalDate metabolicDate, LocalDateTime occurredAt, String rawText) {
        var parsed = FastLogParser.parse(rawText);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        var p = parsed.get();

        var resolved = resolve(p.foodPhrase(), p.amountPhrase());
        if (resolved == null && p.bareCount()) {
            // "3 slices honey wheat bread": the first food word may be a portion unit.
            var words = p.foodPhrase().split("\\s+", 2);
            if (words.length == 2) {
                resolved = resolve(words[1], p.amountPhrase() + " " + words[0]);
            }
        }
        if (resolved == null) {
            return Optional.empty();
        }

        var success = foodItemLogger.logScaled(resolved.item(), resolved.grams(), occurredAt);
        historyStore.append(metabolicDate, occurredAt, rawText, success.message());
        return Optional.of(success.message());
    }

    private record ResolvedEntry(FoodItem item, double grams) {
    }

    private ResolvedEntry resolve(String foodPhrase, String amountPhrase) {
        var item = foodResolver.resolveExact(foodPhrase);
        if (item.isEmpty()) {
            return null;
        }
        var qty = quantityResolver.resolve(item.get(), amountPhrase);
        if (!(qty instanceof QuantityResolution.Grams grams)) {
            // e.g. calories against an item with no calorie data, or an untaught unit - the
            // model path asks the right question.
            return null;
        }
        return new ResolvedEntry(item.get(), grams.grams());
    }
}