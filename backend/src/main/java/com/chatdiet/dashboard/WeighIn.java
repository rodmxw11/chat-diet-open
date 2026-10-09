package com.chatdiet.dashboard;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One day's actual weigh-in (the earliest reading, if multiple were logged that metabolic day).
 *
 * @param time when that reading was logged - the chart marks late-in-the-day weigh-ins, which run
 *             heavier with the day's food and drink and account for much of the day-to-day noise
 */
public record WeighIn(LocalDate date, double weightLbs, LocalTime time) {
}
