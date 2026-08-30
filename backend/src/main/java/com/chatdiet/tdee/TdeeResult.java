package com.chatdiet.tdee;

/**
 * Outcome of {@link AdaptiveTdeeService#estimate()} - either a computed estimate, or an
 * explanation of why one couldn't be computed yet (not enough weigh-in or food-log history).
 */
public sealed interface TdeeResult permits TdeeResult.Estimate, TdeeResult.Unavailable {

    /**
     * @param estimatedCalories     the back-calculated TDEE, in calories/day
     * @param standardErrorCalories the OLS slope's standard error, converted to calories/day -
     *                              how much confidence to place in the point estimate, not a
     *                              range guarantee
     * @param windowDays            the rolling window this was computed over
     * @param loggedDays            how many of those days had food logged
     * @param weightChangeLbs       the fitted-trend-line weight change implied over the window
     *                              (slope × windowDays; positive = gain)
     * @param caveat                a plain-English warning to show alongside the estimate (e.g.
     *                              a calorie-goal change within the window suggesting a new diet
     *                              phase, where water/glycogen shifts distort the 3500 kcal/lb
     *                              assumption), or {@code null} if none applies
     */
    record Estimate(int estimatedCalories, int standardErrorCalories, int windowDays, int loggedDays,
                     double weightChangeLbs, String caveat) implements TdeeResult {
    }

    record Unavailable(String reason) implements TdeeResult {
    }
}
