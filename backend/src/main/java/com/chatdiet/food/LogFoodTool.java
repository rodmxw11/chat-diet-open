package com.chatdiet.food;

import com.chatdiet.dashboard.DailyMacroCacheService;
import com.chatdiet.fdc.FdcClient;
import com.chatdiet.fdc.FdcDetail;
import com.chatdiet.food.resolve.Candidate;
import com.chatdiet.food.resolve.FoodResolution;
import com.chatdiet.food.resolve.FoodResolver;
import com.chatdiet.food.resolve.QuantityResolution;
import com.chatdiet.food.resolve.QuantityResolver;
import com.chatdiet.fooditem.FoodItem;
import com.chatdiet.fooditem.FoodItemRepository;
import com.chatdiet.fooditem.PortionUnitService;
import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * IntentTool that logs one or more named foods from a single utterance. Identity and quantity are
 * resolved separately: {@link FoodResolver} decides which {@link FoodItem} a phrase refers to
 * (an exact alias hit, or a numbered clarification list when it's ambiguous or unknown - never a
 * silent guess), and {@link QuantityResolver} decides how many grams were eaten. Both must
 * succeed before a row is written. See docs/fixing-substring-problem-spec.md §4.
 *
 * <p>A batch resolves partially: items that resolve are saved immediately into one
 * {@code entry_group_id}; unresolved items are named in a combined clarification, and a follow-up
 * call for just those items (with {@code attachToGroupId} set to the echoed group id) lands in the
 * same group.
 */
@Component
@IntentTool(
        name = "log_food",
        intents = {"log_food"},
        description = "Log one or more named foods from a single utterance. For each item, provide foodRef " +
                "(the food name as spoken) and amountText (the amount phrase verbatim, e.g. \"142g\", \"2\", " +
                "\"a bowl\", or \"\" if unstated) - never invent a gram number yourself, there is no amountGrams " +
                "field; the app resolves the phrase against its own cache and USDA FoodData Central. Always " +
                "still provide your own best-effort estimate of calories/macros/micronutrients on each item as " +
                "a fallback. If the tool's reply is a numbered clarification list (each option tagged with a " +
                "hidden [ref:...] marker), relay only the human-readable options to the user - never show the " +
                "[ref:...] markers - then once they pick one, call log_food again for that item with " +
                "resolvedFoodItemId set to the id from an [ref:item:N] marker, resolvedFdcId from an " +
                "[ref:fdc:N] marker, or useEstimate:true if they picked \"estimate it yourself\" (keep " +
                "totalCalories/macros filled in for that case). If a prior reply named a group id for a " +
                "partial batch, pass that as attachToGroupId on the follow-up call so it joins the same log " +
                "entry group instead of starting a new one. Named foods only - not for UPC-based entries."
)
public class LogFoodTool implements Function<LogFoodRequest, ToolResult> {

    private final FoodEntryRepository foodEntryRepository;
    private final FoodItemRepository foodItemRepository;
    private final FoodResolver foodResolver;
    private final QuantityResolver quantityResolver;
    private final FdcClient fdcClient;
    private final PortionUnitService portionUnitService;
    private final DailyMacroCacheService dailyMacroCacheService;
    private final FoodLogVerificationContext foodLogVerificationContext;

    public LogFoodTool(FoodEntryRepository foodEntryRepository, FoodItemRepository foodItemRepository,
                        FoodResolver foodResolver, QuantityResolver quantityResolver, FdcClient fdcClient,
                        PortionUnitService portionUnitService, DailyMacroCacheService dailyMacroCacheService,
                        FoodLogVerificationContext foodLogVerificationContext) {
        this.foodEntryRepository = foodEntryRepository;
        this.foodItemRepository = foodItemRepository;
        this.foodResolver = foodResolver;
        this.quantityResolver = quantityResolver;
        this.fdcClient = fdcClient;
        this.portionUnitService = portionUnitService;
        this.dailyMacroCacheService = dailyMacroCacheService;
        this.foodLogVerificationContext = foodLogVerificationContext;
    }

    @Override
    public ToolResult apply(LogFoodRequest request) {
        Long groupId = request.attachToGroupId();
        var echoes = new ArrayList<String>();
        var clarifications = new ArrayList<String>();
        FoodEntry lastEntry = null;

        for (var itemReq : request.items()) {
            var outcome = resolveItem(itemReq);
            if (outcome instanceof ItemOutcome.Clarify clarify) {
                clarifications.add(clarify.message());
                continue;
            }
            if (outcome instanceof ItemOutcome.UseEstimate) {
                var logged = logEstimate(itemReq, groupId, request.loggedAt());
                if (logged == null) {
                    clarifications.add("\"" + itemReq.foodRef() + "\": what should I log for calories/macros?");
                    continue;
                }
                groupId = logged.entry().entryGroupId();
                echoes.add(logged.echo());
                lastEntry = logged.entry();
                continue;
            }

            var item = ((ItemOutcome.ResolvedTo) outcome).item();
            var qty = quantityResolver.resolve(item, itemReq.amountText());
            if (!(qty instanceof QuantityResolution.Grams grams)) {
                clarifications.add("\"" + itemReq.foodRef() + "\": how many grams (or what count/unit)?");
                continue;
            }

            var logged = logResolvedItem(item, itemReq, grams, groupId, request.loggedAt());
            groupId = logged.entry().entryGroupId();
            echoes.add(logged.echo());
            lastEntry = logged.entry();
        }

        if (echoes.isEmpty()) {
            return new ToolResult.NeedsClarification(String.join("\n\n", clarifications), null);
        }
        var body = String.join("\n", echoes);
        if (!clarifications.isEmpty()) {
            return new ToolResult.NeedsClarification(
                    body + "\n\n(group #" + groupId + ") " + String.join("\n\n", clarifications), lastEntry);
        }
        return new ToolResult.Success(body, lastEntry);
    }

    private sealed interface ItemOutcome {
        record ResolvedTo(FoodItem item) implements ItemOutcome {
        }

        record UseEstimate() implements ItemOutcome {
        }

        record Clarify(String message) implements ItemOutcome {
        }
    }

    private ItemOutcome resolveItem(LogFoodItemRequest itemReq) {
        if (Boolean.TRUE.equals(itemReq.useEstimate())) {
            return new ItemOutcome.UseEstimate();
        }

        if (itemReq.resolvedFoodItemId() != null) {
            var item = foodItemRepository.findById(itemReq.resolvedFoodItemId())
                    .filter(i -> i.deletedAt() == null);
            if (item.isEmpty()) {
                return new ItemOutcome.Clarify(
                        "That cached item for \"" + itemReq.foodRef() + "\" is gone now - try again.");
            }
            var wasAmbiguous = foodResolver.resolve(itemReq.foodRef()) instanceof FoodResolution.Ambiguous;
            foodResolver.confirmSelection(itemReq.foodRef(), item.get().id(), wasAmbiguous);
            return new ItemOutcome.ResolvedTo(item.get());
        }

        if (itemReq.resolvedFdcId() != null) {
            var detail = fdcClient.fetchDetail(itemReq.resolvedFdcId());
            if (detail.isEmpty()) {
                return new ItemOutcome.Clarify(
                        "Couldn't fetch nutrition for that FDC match for \"" + itemReq.foodRef() + "\" - try again.");
            }
            var item = cacheFdcItem(detail.get());
            foodResolver.confirmSelection(itemReq.foodRef(), item.id(), false);
            return new ItemOutcome.ResolvedTo(item);
        }

        var resolution = foodResolver.resolve(itemReq.foodRef());
        return switch (resolution) {
            case FoodResolution.Resolved r -> new ItemOutcome.ResolvedTo(r.item());
            case FoodResolution.Ambiguous a ->
                    new ItemOutcome.Clarify(renderCandidates(itemReq.foodRef(), a.candidates(), true));
            case FoodResolution.Unknown u ->
                    new ItemOutcome.Clarify(renderCandidates(itemReq.foodRef(), u.candidates(), false));
        };
    }

    private FoodItem cacheFdcItem(FdcDetail detail) {
        var product = detail.product();
        var item = foodItemRepository.save(new FoodItem(product.name(), null,
                product.caloriesPer100g(), product.proteinPer100g(), product.carbsPer100g(), product.fatPer100g(),
                product.fiberPer100g(), product.sugarPer100g(), product.sodiumMgPer100g(),
                product.saturatedFatPer100g(), product.cholesterolMgPer100g(), product.potassiumMgPer100g(),
                product.typicalServingG(), "FDC"));
        if (!detail.portions().isEmpty()) {
            portionUnitService.storePortions(item.id(), detail.portions());
        }
        return item;
    }

    private record Logged(FoodEntry entry, String echo) {
    }

    /** Logs a resolved item, assigning it to {@code groupId} or starting a new self-referential group. */
    private Logged logResolvedItem(FoodItem item, LogFoodItemRequest itemReq, QuantityResolution.Grams grams,
                                    Long groupId, LocalDateTime loggedAt) {
        var scaled = item.scaledTo(grams.grams());
        var entry = new FoodEntry(LoggedAtResolver.resolve(loggedAt), itemReq.foodRef(), scaled.calories(),
                scaled.proteinG(), scaled.carbsG(), scaled.fatG(), scaled.fiberG(), scaled.sugarG(),
                scaled.sodiumMg(), scaled.saturatedFatG(), scaled.cholesterolMg(), scaled.potassiumMg(),
                item.id(), grams.grams(), item.lookupSource());
        entry = persistIntoGroup(entry, groupId);

        foodItemRepository.save(item.withUsageBumped());
        if (grams.teachUnitName() != null && grams.teachUnitCount() != null) {
            portionUnitService.learnFromWeighedEntry(item.id(), grams.teachUnitName(), grams.teachUnitCount(),
                    grams.grams());
        }
        dailyMacroCacheService.recomputeForTimestamp(entry.loggedAt());
        foodLogVerificationContext.markLogged();

        var echo = "%s (%.0fg) → %s (%s) — %d cal"
                .formatted(itemReq.foodRef(), grams.grams(), item.name(), tierTag(item.lookupSource()),
                        scaled.calories());
        return new Logged(entry, echo);
    }

    /** Tier 4: no identity resolved - the model's estimate, explicitly chosen from a clarification list. */
    private Logged logEstimate(LogFoodItemRequest itemReq, Long groupId, LocalDateTime loggedAt) {
        if (itemReq.totalCalories() == null) {
            return null;
        }

        Long newFoodItemId = null;
        var explicit = QuantityResolver.parseExplicitGrams(itemReq.amountText());
        if (explicit != null) {
            double factor = 100.0 / explicit.grams();
            var item = foodItemRepository.save(new FoodItem(itemReq.foodRef(), null,
                    itemReq.totalCalories() * factor, scale(itemReq.totalProteinG(), factor),
                    scale(itemReq.totalCarbsG(), factor), scale(itemReq.totalFatG(), factor),
                    scale(itemReq.fiberG(), factor), scale(itemReq.sugarG(), factor),
                    scale(itemReq.sodiumMg(), factor), scale(itemReq.saturatedFatG(), factor),
                    scale(itemReq.cholesterolMg(), factor), scale(itemReq.potassiumMg(), factor),
                    null, "MODEL_ESTIMATE"));
            newFoodItemId = item.id();
            foodResolver.confirmSelection(itemReq.foodRef(), item.id(), false);
            if (explicit.teachUnitName() != null && explicit.teachUnitCount() != null) {
                portionUnitService.learnFromWeighedEntry(item.id(), explicit.teachUnitName(),
                        explicit.teachUnitCount(), explicit.grams());
            }
        }

        var entry = new FoodEntry(LoggedAtResolver.resolve(loggedAt), itemReq.foodRef(), itemReq.totalCalories(),
                itemReq.totalProteinG(), itemReq.totalCarbsG(), itemReq.totalFatG(), itemReq.fiberG(),
                itemReq.sugarG(), itemReq.sodiumMg(), itemReq.saturatedFatG(), itemReq.cholesterolMg(),
                itemReq.potassiumMg(), newFoodItemId, explicit != null ? explicit.grams() : null, "MANUAL");
        entry = persistIntoGroup(entry, groupId);
        dailyMacroCacheService.recomputeForTimestamp(entry.loggedAt());
        foodLogVerificationContext.markLogged();

        var echo = "%s → %d cal (estimate)".formatted(itemReq.foodRef(), itemReq.totalCalories());
        return new Logged(entry, echo);
    }

    /** Saves the entry into {@code groupId}, or starts a new self-referential group if none is open yet. */
    private FoodEntry persistIntoGroup(FoodEntry entry, Long groupId) {
        if (groupId != null) {
            return foodEntryRepository.save(entry.withEntryGroupId(groupId));
        }
        var saved = foodEntryRepository.save(entry);
        return foodEntryRepository.save(saved.withEntryGroupId(saved.id()));
    }

    private static String tierTag(String lookupSource) {
        return switch (lookupSource) {
            case "MODEL_ESTIMATE" -> "estimate";
            case "MANUAL" -> "manual";
            default -> lookupSource;
        };
    }

    private String renderCandidates(String foodRef, List<Candidate> candidates, boolean ambiguous) {
        var sb = new StringBuilder();
        sb.append(ambiguous ? "Which \"" : "Couldn't find \"").append(foodRef)
                .append(ambiguous ? "\" did you mean?" : "\" - which of these?").append('\n');
        var i = 1;
        for (var c : candidates) {
            sb.append(i++).append(") ").append(c.label());
            if (c.foodItemId() != null) {
                sb.append(" [ref:item:").append(c.foodItemId()).append(']');
            } else if (c.fdcId() != null) {
                sb.append(" [ref:fdc:").append(c.fdcId()).append(']');
            } else if (c.isEstimateOption()) {
                sb.append(" [ref:estimate]");
            }
            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static Double scale(Double value, double factor) {
        return value != null ? value * factor : null;
    }
}
