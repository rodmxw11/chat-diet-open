package com.chatdiet.food;

import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * IntentTool that lists every food entry logged on a given metabolic day, grouped by
 * {@code entry_group_id} with a per-group and a day total. Exists as a small, deterministic
 * alternative to {@code run_sql} for what is by far the most common "what did I eat on X"
 * question, so a plain lookup doesn't depend on the model reliably choosing to compose and run SQL
 * for it (in practice it doesn't always).
 */
@Component
@IntentTool(
        name = "list_food_entries",
        intents = {"query_data"},
        description = "List every food entry logged on a specific day (today, yesterday, or a named date), grouped by logging turn, with a calorie total."
)
public class ListFoodEntriesTool implements Function<ListFoodEntriesRequest, ToolResult> {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");

    private final FoodEntryRepository foodEntryRepository;
    private final DayBoundaryService dayBoundaryService;

    public ListFoodEntriesTool(FoodEntryRepository foodEntryRepository, DayBoundaryService dayBoundaryService) {
        this.foodEntryRepository = foodEntryRepository;
        this.dayBoundaryService = dayBoundaryService;
    }

    /**
     * @return a {@link ToolResult.NotFound} if nothing was logged that day, otherwise a
     *         {@link ToolResult.Success} listing each entry, grouped by logging turn with a
     *         per-group subtotal, plus the day's calorie total
     */
    @Override
    public ToolResult apply(ListFoodEntriesRequest request) {
        var start = dayBoundaryService.startOfMetabolicDay(request.date());
        var end = dayBoundaryService.endOfMetabolicDay(request.date());
        var entries = foodEntryRepository.findByLoggedAtBetween(start, end);

        if (entries.isEmpty()) {
            return new ToolResult.NotFound("any food logged on " + request.date());
        }

        int totalCalories = entries.stream().mapToInt(e -> e.totalCalories() != null ? e.totalCalories() : 0).sum();

        var groups = entries.stream()
                .collect(Collectors.groupingBy(
                        e -> e.entryGroupId() != null ? e.entryGroupId() : e.id(),
                        java.util.LinkedHashMap::new, Collectors.toList()));

        var body = groups.values().stream()
                .sorted(Comparator.comparing(group -> group.get(0).loggedAt()))
                .map(group -> {
                    var lines = group.stream()
                            .map(e -> "- %s (%s): %d kcal".formatted(e.rawUtterance(), e.loggedAt().format(TIME_FORMAT),
                                    e.totalCalories() != null ? e.totalCalories() : 0))
                            .collect(Collectors.joining("\n"));
                    if (group.size() == 1) {
                        return lines;
                    }
                    var groupTotal = group.stream().mapToInt(e -> e.totalCalories() != null ? e.totalCalories() : 0).sum();
                    return lines + "\n  Subtotal: %d kcal".formatted(groupTotal);
                })
                .collect(Collectors.joining("\n"));

        return new ToolResult.Success(
                "%s\nTotal: %d kcal across %d %s.".formatted(
                        body, totalCalories, entries.size(), entries.size() == 1 ? "entry" : "entries"),
                entries);
    }
}
