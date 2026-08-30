package com.chatdiet.tdee;

/**
 * Outcome of {@link AdaptiveTdeeService#estimate()} - either a computed estimate, or an
 * explanation of why one couldn't be computed yet (not enough weigh-in or food-log history).
 */
public sealed interface TdeeResult permits TdeeResult.Estimate, TdeeResult.Unavailable {

    /**
     * @param estimatedCalories the back-calculated TDEE, in calories/day
     * @param windowDays        the rolling window this was computed over
     * @param loggedDays        how many of those days had food logged
     * @param weightChangeLbs   the smoothed-trend weight change over the window (positive = gain)
     */
    record Estimate(int estimatedCalories, int windowDays, int loggedDays, double weightChangeLbs)
            implements TdeeResult {
    }

    record Unavailable(String reason) implements TdeeResult {
    }
}
