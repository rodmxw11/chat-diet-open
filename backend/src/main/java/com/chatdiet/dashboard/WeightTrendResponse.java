package com.chatdiet.dashboard;

import java.util.List;

/**
 * Response for {@code GET /api/dashboard/weight-trend}.
 *
 * @param goal null if no weigh-in exists yet to anchor a goal line from
 */
public record WeightTrendResponse(List<WeighIn> actual, List<TrendPoint> smoothed, GoalLine goal) {
}
