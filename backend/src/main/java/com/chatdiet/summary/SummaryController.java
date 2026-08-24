package com.chatdiet.summary;

import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.food.FoodEntryRepository;
import com.chatdiet.nutrition.GoalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST surface for the header's today-at-a-glance summary, polled independently of chat. */
@RestController
public class SummaryController {

    private final GoalService goalService;
    private final FoodEntryRepository foodEntryRepository;
    private final DayBoundaryService dayBoundaryService;

    public SummaryController(GoalService goalService, FoodEntryRepository foodEntryRepository,
                              DayBoundaryService dayBoundaryService) {
        this.goalService = goalService;
        this.foodEntryRepository = foodEntryRepository;
        this.dayBoundaryService = dayBoundaryService;
    }

    @GetMapping("/api/summary/today")
    public TodaySummary today() {
        var today = dayBoundaryService.today();
        var start = dayBoundaryService.startOfMetabolicDay(today);
        var end = dayBoundaryService.endOfMetabolicDay(today);
        var entries = foodEntryRepository.findByLoggedAtBetween(start, end);

        int consumed = entries.stream().mapToInt(e -> e.totalCalories() != null ? e.totalCalories() : 0).sum();
        var target = goalService.targetFor(today).map(t -> t.targetCalories()).orElse(null);
        Integer remaining = target != null ? target - consumed : null;

        return new TodaySummary(today, target, consumed, remaining, entries.size());
    }
}
