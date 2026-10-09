package com.chatdiet.measurement;

/**
 * The US Navy circumference method for estimating body-fat percentage (Hodgdon and Beckett), in
 * its inches form. Typically within a few percentage points of a DEXA scan - good for tracking the
 * trend, not a precise single reading.
 *
 * <ul>
 *   <li>Men: {@code 86.010·log10(waist − neck) − 70.041·log10(height) + 36.76}</li>
 *   <li>Women: {@code 163.205·log10(waist + hip − neck) − 97.684·log10(height) − 78.387}</li>
 * </ul>
 */
public final class NavyBodyFat {

    private NavyBodyFat() {
    }

    /**
     * @param sex      "male"/"female" (or anything starting m/f); other values give no estimate
     * @param heightIn height in inches
     * @return body fat %, rounded to 0.1, or null when an input the formula needs is missing or
     *         the measurements can't produce a meaningful value (e.g. neck at least as big as waist)
     */
    public static Double estimate(String sex, Double heightIn, Double waistIn, Double neckIn, Double hipIn) {
        if (sex == null || sex.isBlank() || heightIn == null || heightIn <= 0 || waistIn == null || neckIn == null) {
            return null;
        }
        double percent;
        switch (Character.toLowerCase(sex.strip().charAt(0))) {
            case 'm' -> {
                if (waistIn <= neckIn) {
                    return null;
                }
                percent = 86.010 * Math.log10(waistIn - neckIn) - 70.041 * Math.log10(heightIn) + 36.76;
            }
            case 'f' -> {
                if (hipIn == null || waistIn + hipIn <= neckIn) {
                    return null;
                }
                percent = 163.205 * Math.log10(waistIn + hipIn - neckIn) - 97.684 * Math.log10(heightIn) - 78.387;
            }
            default -> {
                return null;
            }
        }
        return Math.round(percent * 10) / 10.0;
    }
}
