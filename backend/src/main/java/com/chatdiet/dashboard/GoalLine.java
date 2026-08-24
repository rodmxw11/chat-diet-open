package com.chatdiet.dashboard;

import java.time.LocalDate;

/**
 * A straight-line weight goal trajectory: {@code startWeightLbs} on {@code startDate}, changing
 * by {@code dailyRateLbs} per day thereafter. The frontend draws it as a line/reference line
 * rather than the backend materializing every point.
 */
public record GoalLine(LocalDate startDate, double startWeightLbs, double dailyRateLbs) {
}
