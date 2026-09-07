package com.chatdiet.food;

import com.chatdiet.chat.ConversationHistoryStore;
import com.chatdiet.dashboard.DailyMacroCacheService;
import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.food.resolve.QuantityResolution;
import com.chatdiet.food.resolve.QuantityResolver;
import com.chatdiet.fooditem.FoodItemLogger;
import com.chatdiet.fooditem.FoodItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

/**
 * REST surface for food entries: the read-only Daily Foods screen, per-row delete, and the
 * barcode quantity prompt's direct create path ({@link #create}) - the one food-entry write that
 * doesn't go through chat, though it still appends a chat exchange so the transcript and the
 * model's context stay complete.
 */
@RestController
public class FoodEntriesController {

    private final FoodEntryRepository foodEntryRepository;
    private final DayBoundaryService dayBoundaryService;
    private final DailyMacroCacheService dailyMacroCacheService;
    private final FoodItemRepository foodItemRepository;
    private final QuantityResolver quantityResolver;
    private final FoodItemLogger foodItemLogger;
    private final ConversationHistoryStore historyStore;

    public FoodEntriesController(FoodEntryRepository foodEntryRepository, DayBoundaryService dayBoundaryService,
                                  DailyMacroCacheService dailyMacroCacheService,
                                  FoodItemRepository foodItemRepository, QuantityResolver quantityResolver,
                                  FoodItemLogger foodItemLogger, ConversationHistoryStore historyStore) {
        this.foodEntryRepository = foodEntryRepository;
        this.dayBoundaryService = dayBoundaryService;
        this.dailyMacroCacheService = dailyMacroCacheService;
        this.foodItemRepository = foodItemRepository;
        this.quantityResolver = quantityResolver;
        this.foodItemLogger = foodItemLogger;
        this.historyStore = historyStore;
    }

    /** Returns every food entry logged on the given metabolic day, chronologically. */
    @GetMapping("/api/food-entries")
    public List<FoodEntry> byDate(@RequestParam LocalDate date) {
        var start = dayBoundaryService.startOfMetabolicDay(date);
        var end = dayBoundaryService.endOfMetabolicDay(date);
        return foodEntryRepository.findByLoggedAtBetween(start, end);
    }

    /**
     * Logs a food entry directly by cached-item id and one explicit quantity - the terminus of a
     * barcode scan's quantity prompt. Backdates via {@code clientSentAt} (an offline-queued
     * submit files under the day it was composed) and persists a "Scanned ..." chat exchange so
     * the turn shows in history and in the model's context window.
     */
    @PostMapping("/api/food-entries")
    public FoodEntryCreateResponse create(@RequestBody FoodEntryCreateRequest request) {
        if (request.foodItemId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "foodItemId is required");
        }
        var providedQuantities = Stream.of(request.grams(), request.kcal(), request.servings())
                .filter(q -> q != null && q > 0)
                .count();
        if (providedQuantities != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "provide exactly one positive quantity: grams, kcal, or servings");
        }

        var item = foodItemRepository.findById(request.foodItemId())
                .filter(i -> i.deletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        var qty = quantityResolver.resolve(item, request.grams(), request.servings(), request.kcal());
        if (!(qty instanceof QuantityResolution.Grams grams)) {
            // e.g. kcal against an item with no per-100g calorie figure.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "that quantity can't be converted for this item - enter grams instead");
        }

        var occurredAt = dayBoundaryService.occurredAt(request.clientSentAt());
        var success = foodItemLogger.logScaled(item, grams.grams(), occurredAt);
        historyStore.append(dayBoundaryService.metabolicDateOf(occurredAt), occurredAt,
                "Scanned " + item.name() + ": " + amountAsEntered(request), success.message());
        return new FoodEntryCreateResponse(success.message(), (FoodEntry) success.payload());
    }

    /** Deletes a single food entry and recomputes that day's macro cache to match. */
    @DeleteMapping("/api/food-entries/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        var entry = foodEntryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        foodEntryRepository.deleteById(id);
        dailyMacroCacheService.recomputeForTimestamp(entry.loggedAt());
        return ResponseEntity.noContent().build();
    }

    private static String amountAsEntered(FoodEntryCreateRequest request) {
        if (request.grams() != null && request.grams() > 0) {
            return trimNumber(request.grams()) + "g";
        }
        if (request.kcal() != null && request.kcal() > 0) {
            return trimNumber(request.kcal()) + " cal";
        }
        return trimNumber(request.servings()) + (request.servings() == 1.0 ? " serving" : " servings");
    }

    private static String trimNumber(Double value) {
        return value == Math.floor(value) ? String.valueOf(value.longValue()) : String.valueOf(value);
    }
}