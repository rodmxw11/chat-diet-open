package com.chatdiet.vitals;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;

public interface VitalsEntryRepository extends ListCrudRepository<VitalsEntry, Long> {

    @Query("SELECT * FROM vitals_entry ORDER BY logged_at DESC LIMIT 1")
    Optional<VitalsEntry> findMostRecent();
}
