package com.chatdiet.omron;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Spring Data JDBC repository for {@link OmronReading}. */
public interface OmronReadingRepository extends ListCrudRepository<OmronReading, Long> {

    /** Readings at or after {@code since}, oldest first. */
    @Query("SELECT * FROM omron_reading WHERE timestamp >= :since ORDER BY timestamp")
    List<OmronReading> findByTimestampGreaterThanEqualOrderByTimestamp(LocalDateTime since);

    /** The most recent reading of all, if any have been imported. */
    @Query("SELECT * FROM omron_reading ORDER BY timestamp DESC LIMIT 1")
    Optional<OmronReading> findLatest();
}
