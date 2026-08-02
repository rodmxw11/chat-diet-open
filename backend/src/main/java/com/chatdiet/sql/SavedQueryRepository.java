package com.chatdiet.sql;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;

public interface SavedQueryRepository extends ListCrudRepository<SavedQuery, Long> {

    @Query("SELECT * FROM saved_query WHERE name = :name")
    Optional<SavedQuery> findByName(String name);

    @Query("SELECT * FROM saved_query ORDER BY use_count DESC")
    List<SavedQuery> findAllOrderByUseCountDesc();
}
