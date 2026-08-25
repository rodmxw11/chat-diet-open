package com.chatdiet.food;

import com.chatdiet.day.DayBoundaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** REST surface for the read-only Daily Foods screen. */
@RestController
public class FoodEntriesController {

    private final FoodEntryRepository foodEntryRepository;
    private final DayBoundaryService dayBoundaryService;

    public FoodEntriesController(FoodEntryRepository foodEntryRepository, DayBoundaryService dayBoundaryService) {
        this.foodEntryRepository = foodEntryRepository;
        this.dayBoundaryService = dayBoundaryService;
    }

    /** Returns every food entry logged on the given metabolic day, chronologically. */
    @GetMapping("/api/food-entries")
    public List<FoodEntry> byDate(@RequestParam LocalDate date) {
        var start = dayBoundaryService.startOfMetabolicDay(date);
        var end = dayBoundaryService.endOfMetabolicDay(date);
        return foodEntryRepository.findByLoggedAtBetween(start, end);
    }
}
