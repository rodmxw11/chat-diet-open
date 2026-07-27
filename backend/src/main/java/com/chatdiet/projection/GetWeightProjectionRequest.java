package com.chatdiet.projection;

import java.time.LocalDate;

public record GetWeightProjectionRequest(Double goalWeightLbs, LocalDate goalDate) {
}
