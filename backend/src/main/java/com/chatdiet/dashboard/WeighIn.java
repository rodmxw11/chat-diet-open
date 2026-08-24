package com.chatdiet.dashboard;

import java.time.LocalDate;

/** One day's actual weigh-in (averaged if multiple readings were logged that metabolic day). */
public record WeighIn(LocalDate date, double weightLbs) {
}
