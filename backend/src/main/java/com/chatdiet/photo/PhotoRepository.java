package com.chatdiet.photo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.time.LocalDateTime;
import java.util.List;

/** Spring Data JDBC repository for archived {@link Photo} records. */
public interface PhotoRepository extends ListCrudRepository<Photo, Long> {

    /** Returns all photos whose {@code purgeAfter} timestamp is before {@code cutoff}. */
    @Query("SELECT * FROM photo WHERE purge_after < :cutoff")
    List<Photo> findDueForPurge(LocalDateTime cutoff);
}
