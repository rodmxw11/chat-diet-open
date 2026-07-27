package com.chatdiet.day;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class DayBoundaryService {

    @Value("${chat-diet.day-rollover-hour:4}")
    private int dayRolloverHour;

    public LocalDate metabolicDateOf(LocalDateTime timestamp) {
        return timestamp.getHour() < dayRolloverHour
                ? timestamp.toLocalDate().minusDays(1)
                : timestamp.toLocalDate();
    }

    public LocalDateTime startOfMetabolicDay(LocalDate metabolicDate) {
        return metabolicDate.atTime(dayRolloverHour, 0);
    }

    public LocalDateTime endOfMetabolicDay(LocalDate metabolicDate) {
        return startOfMetabolicDay(metabolicDate.plusDays(1));
    }

    public LocalDate today() {
        return metabolicDateOf(LocalDateTime.now());
    }
}
