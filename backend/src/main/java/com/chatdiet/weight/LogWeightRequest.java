package com.chatdiet.weight;

/** Request for {@code log_weight}: a body weight reading in pounds. */
public record LogWeightRequest(double weightLbs) {
}
