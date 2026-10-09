package com.chatdiet.measurement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the measurement history with a US Navy body-fat estimate per session, using the
 * {@code chat-diet.profile.sex} and {@code height-in} settings. Neck (and, for women, hip) change
 * slowly, so a session that only measured the waist reuses the most recent earlier neck/hip - and
 * says so, via the {@code neckCarried}/{@code hipCarried} flags - rather than going without an
 * estimate.
 */
@Service
public class MeasurementService {

    /**
     * One session as shown on the Measurements page.
     *
     * @param bodyFatPct  Navy estimate, or null if the profile or a needed measurement is missing
     * @param neckCarried true when the estimate used an earlier session's neck
     * @param hipCarried  true when the estimate used an earlier session's hip
     */
    public record MeasurementView(Long id, LocalDateTime measuredAt, Double waistIn, Double neckIn, Double hipIn,
                                  Double bodyFatPct, boolean neckCarried, boolean hipCarried) {
    }

    private final BodyMeasurementRepository repository;
    private final String sex;
    private final double heightIn;

    public MeasurementService(BodyMeasurementRepository repository,
                              @Value("${chat-diet.profile.sex:}") String sex,
                              @Value("${chat-diet.profile.height-in:0}") double heightIn) {
        this.repository = repository;
        this.sex = sex;
        this.heightIn = heightIn;
    }

    /** True when sex and height are configured, so body fat can be estimated at all. */
    public boolean profileComplete() {
        return !sex.isBlank() && heightIn > 0;
    }

    /** Every session, newest first, each with its body-fat estimate. */
    public List<MeasurementView> history() {
        var views = new ArrayList<MeasurementView>();
        Double lastNeck = null;
        Double lastHip = null;
        for (var m : repository.findAllOldestFirst()) {
            var neck = m.neckIn() != null ? m.neckIn() : lastNeck;
            var hip = m.hipIn() != null ? m.hipIn() : lastHip;
            var bodyFat = NavyBodyFat.estimate(sex, heightIn, m.waistIn(), neck, hip);
            views.add(new MeasurementView(m.id(), m.measuredAt(), m.waistIn(), m.neckIn(), m.hipIn(), bodyFat,
                    bodyFat != null && m.neckIn() == null,
                    bodyFat != null && m.hipIn() == null && isFemale()));
            if (m.neckIn() != null) {
                lastNeck = m.neckIn();
            }
            if (m.hipIn() != null) {
                lastHip = m.hipIn();
            }
        }
        return views.reversed();
    }

    private boolean isFemale() {
        return !sex.isBlank() && Character.toLowerCase(sex.strip().charAt(0)) == 'f';
    }
}
