package com.chatdiet.day;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Defines the app's "metabolic day" concept, used throughout the app (e.g. daily nutrition
 * totals, fasting windows) in place of the calendar day. A metabolic day does not roll over at
 * midnight; instead it rolls over at a configurable hour (default 4am, {@code
 * chat-diet.day-rollover-hour}), so that eating/activity that happens shortly after midnight is
 * still attributed to the previous calendar day rather than starting a new one.
 */
@Service
public class DayBoundaryService {

    @Value("${chat-diet.day-rollover-hour:4}")
    private int dayRolloverHour;

    /**
     * Maps a timestamp to the metabolic date it belongs to: timestamps before the rollover hour
     * belong to the previous calendar day.
     */
    public LocalDate metabolicDateOf(LocalDateTime timestamp) {
        return timestamp.getHour() < dayRolloverHour
                ? timestamp.toLocalDate().minusDays(1)
                : timestamp.toLocalDate();
    }

    /** Returns the timestamp at which the given metabolic date begins (the rollover hour of that calendar date). */
    public LocalDateTime startOfMetabolicDay(LocalDate metabolicDate) {
        return metabolicDate.atTime(dayRolloverHour, 0);
    }

    /** Returns the timestamp at which the given metabolic date ends (the rollover hour of the following calendar date). */
    public LocalDateTime endOfMetabolicDay(LocalDate metabolicDate) {
        return startOfMetabolicDay(metabolicDate.plusDays(1));
    }

    /** Returns the current metabolic date, i.e. {@link #metabolicDateOf} applied to now. */
    public LocalDate today() {
        return metabolicDateOf(LocalDateTime.now());
    }

    /**
     * Maps an instant to its metabolic date <em>in the server's local zone</em>. Clients send
     * timestamps as UTC instants, so the conversion has to happen somewhere; doing it here keeps
     * it in one place, because getting it wrong silently files everything into the wrong day for
     * anyone not running in UTC.
     */
    public LocalDate metabolicDateOf(Instant instant) {
        return metabolicDateOf(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
    }

    /**
     * Resolves when a message was really composed, given the client's self-reported timestamp.
     * Used to file offline-queued messages under the day they were written rather than the day
     * they finally synced.
     *
     * @param clientSentAt an ISO-8601 instant from the client, or null/malformed to just use now
     * @return the composition time, never in the future - a device with a fast clock would
     *         otherwise write rows dated tomorrow, which the history endpoint would then hide
     *         from the user indefinitely. Timestamps in the past are trusted: that is the
     *         legitimate offline-replay case.
     */
    public LocalDateTime occurredAt(String clientSentAt) {
        var now = LocalDateTime.now();
        if (clientSentAt == null) {
            return now;
        }
        try {
            var sentAt = LocalDateTime.ofInstant(Instant.parse(clientSentAt), ZoneId.systemDefault());
            return sentAt.isAfter(now) ? now : sentAt;
        } catch (Exception ignored) {
            // Malformed timestamp - not worth failing the request over.
            return now;
        }
    }
}
