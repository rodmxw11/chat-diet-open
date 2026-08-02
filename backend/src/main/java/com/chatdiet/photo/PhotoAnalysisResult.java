package com.chatdiet.photo;

/**
 * Outcome of {@link PhotoAnalysisService#analyze}: a best-effort nutrition estimate for a food
 * photo, plus whether the model was confident enough to treat it as final.
 *
 * @param confident whether the estimate can be treated as final; when {@code false}, callers
 *                  should surface {@code question} instead of the numbers, though the numeric
 *                  fields are still filled with a best-effort estimate
 * @param question  clarifying question to ask the user when {@code confident} is {@code false};
 *                  {@code null} otherwise
 */
public record PhotoAnalysisResult(
        String description,
        int calories,
        double proteinG,
        double carbsG,
        double fatG,
        boolean confident,
        String question
) {
}
