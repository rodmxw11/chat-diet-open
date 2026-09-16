package com.chatdiet.dashboard;

import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.omron.OmronReadingRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** Builds the blood pressure chart's data from imported OMRON readings. */
@Service
public class BloodPressureService {

    private final OmronReadingRepository omronReadingRepository;
    private final DayBoundaryService dayBoundaryService;

    public BloodPressureService(OmronReadingRepository omronReadingRepository, DayBoundaryService dayBoundaryService) {
        this.omronReadingRepository = omronReadingRepository;
        this.dayBoundaryService = dayBoundaryService;
    }

    /** Every reading in the last {@code days} metabolic days (7 or 30), oldest first. */
    public List<BloodPressureReading> readings(int days) {
        var today = dayBoundaryService.today();
        var since = dayBoundaryService.startOfMetabolicDay(today.minusDays(Math.max(days, 1) - 1L));
        return omronReadingRepository.findByTimestampGreaterThanEqualOrderByTimestamp(since).stream()
                .map(r -> new BloodPressureReading(r.timestamp(), r.systolic(), r.diastolic(), r.bpm()))
                .toList();
    }
}
