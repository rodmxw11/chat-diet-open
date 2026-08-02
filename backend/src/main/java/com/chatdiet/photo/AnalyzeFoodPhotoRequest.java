package com.chatdiet.photo;

/**
 * Request for the {@code analyze_food_photo} tool.
 *
 * @param hint optional user-supplied context about the photo (e.g. clarifying what's shown);
 *             may be {@code null} or blank
 */
public record AnalyzeFoodPhotoRequest(String hint) {
}
