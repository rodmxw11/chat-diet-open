package com.chatdiet.dashboard;

import java.time.LocalDate;

/** One day's macro totals for the stacked macro bar chart. */
public record DailyMacros(LocalDate date, double proteinG, double carbsG, double fatG, int calories) {
}
