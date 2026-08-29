package com.chatdiet.food;

import com.chatdiet.dashboard.DailyMacroCacheService;
import com.chatdiet.day.DayBoundaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;

/** REST surface for the read-only Daily Foods screen. */
@RestController
public class FoodEntriesController {

    private final FoodEntryRepository foodEntryRepository;
    private final DayBoundaryService dayBoundaryService;
    private final DailyMacroCacheService dailyMacroCacheService;

    public FoodEntriesController(FoodEntryRepository foodEntryRepository, DayBoundaryService dayBoundaryService,
                                  DailyMacroCacheService dailyMacroCacheService) {
        this.foodEntryRepository = foodEntryRepository;
        this.dayBoundaryService = dayBoundaryService;
        this.dailyMacroCacheService = dailyMacroCacheService;
    }

    /** Returns every food entry logged on the given metabolic day, chronologically. */
    @GetMapping("/api/food-entries")
    public List<FoodEntry> byDate(@RequestParam LocalDate date) {
        var start = dayBoundaryService.startOfMetabolicDay(date);
        var end = dayBoundaryService.endOfMetabolicDay(date);
        return foodEntryRepository.findByLoggedAtBetween(start, end);
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
}
