package com.chatdiet.sql;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;

/** Spring Data JDBC repository for {@link SavedQuery}. */
public interface SavedQueryRepository extends ListCrudRepository<SavedQuery, Long> {

    /** Looks up a saved query by its exact (case-sensitive) name. */
    @Query("SELECT * FROM saved_query WHERE name = :name")
    Optional<SavedQuery> findByName(String name);

    /** Returns all saved queries, most-reused first, for inclusion in the composer's prompt. */
    @Query("SELECT * FROM saved_query ORDER BY use_count DESC")
    List<SavedQuery> findAllOrderByUseCountDesc();
}
