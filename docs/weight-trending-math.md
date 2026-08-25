# Weight trend math

The weight trend line (`WeightTrendService`) follows the same algorithm as
[TrendWeight](https://trendweight.com/math), adapted from its
[open-source implementation](https://github.com/ervwalter/trendweight).

## Algorithm

1. **One reading per day.** If multiple weigh-ins land on the same metabolic
   day, the earliest one wins.
2. **Interpolate gaps.** Missing days between two real weigh-ins are filled
   with linearly interpolated values, so a multi-day gap eases the trend
   across it one day at a time instead of jumping straight from the day
   before the gap to the day after it.
3. **Exponential smoothing.** The trend is seeded with the first-ever
   weigh-in, then each subsequent day (real or interpolated) updates it:

   ```
   trend = trend + 0.1 * (actualWeight - trend)
   ```

   That is, each day's trend is 90% the previous trend and 10% today's
   (real or interpolated) weight.

4. **Full history, windowed display.** The smoothing runs over the entire
   weigh-in history, not just the displayed window - restarting the seed at
   a window boundary would distort the trend for weeks after any window
   shift, since it takes many data points for the smoothing to converge.
   Only the requested window (30 days) is returned to the frontend.

## Not implemented

TrendWeight also smooths body-fat percentage/mass alongside weight; this app
doesn't track body fat, so only the weight trend applies.
