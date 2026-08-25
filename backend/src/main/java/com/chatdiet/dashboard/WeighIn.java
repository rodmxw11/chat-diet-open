package com.chatdiet.dashboard;

import java.time.LocalDate;

/** One day's actual weigh-in (the earliest reading, if multiple were logged that metabolic day). */
public record WeighIn(LocalDate date, double weightLbs) {
}
