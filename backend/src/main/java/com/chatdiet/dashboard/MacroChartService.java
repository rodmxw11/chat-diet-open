package com.chatdiet.dashboard;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the per-day macro breakdown for the macro bar chart from {@link DailyMacroCache}, which
 * {@link DailyMacroCacheService} keeps in sync with food_entry - avoids re-summing every food
 * entry in range on every request, which matters most for the 30-day view.
 */
@Service
public class MacroChartService {

    private final DailyMacroCacheRepository dailyMacroCacheRepository;

    public MacroChartService(DailyMacroCacheRepository dailyMacroCacheRepository) {
        this.dailyMacroCacheRepository = dailyMacroCacheRepository;
    }

    /** Returns one {@link DailyMacros} per metabolic day in {@code [from, to]}, oldest first. */
    public List<DailyMacros> dailyMacros(LocalDate from, LocalDate to) {
        var cached = dailyMacroCacheRepository.findByMetabolicDateBetween(from, to).stream()
                .collect(Collectors.toMap(DailyMacroCache::metabolicDate, c -> c));

        var result = new ArrayList<DailyMacros>();
        for (var date = from; !date.isAfter(to); date = date.plusDays(1)) {
            var cache = cached.get(date);
            result.add(cache != null
                    ? new DailyMacros(date, cache.proteinG(), cache.carbsG(), cache.fatG(), cache.totalCalories())
                    : new DailyMacros(date, 0, 0, 0, 0));
        }
        return result;
    }
}
