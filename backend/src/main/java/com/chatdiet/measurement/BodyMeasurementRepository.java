package com.chatdiet.measurement;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

/** Spring Data JDBC repository for {@link BodyMeasurement}. */
public interface BodyMeasurementRepository extends ListCrudRepository<BodyMeasurement, Long> {

    /** Every measurement, oldest first - the order neck/hip carry-forward needs. */
    @Query("SELECT * FROM body_measurement ORDER BY measured_at, id")
    List<BodyMeasurement> findAllOldestFirst();
}
