package com.chatdiet.weight;

/**
 * Request for {@code correct_weight_entry}: the corrected weight in pounds to overwrite the most
 * recently logged weight entry with. Only used when the user's correction is linguistically
 * marked; a bare new number is instead treated as a new {@link LogWeightRequest}.
 */
public record CorrectWeightRequest(double weightLbs) {
}
