package com.chatdiet.photo;

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
