package com.chatdiet.photo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PhotoRepository extends ListCrudRepository<Photo, Long> {

    @Query("SELECT * FROM photo WHERE purge_after < :cutoff")
    List<Photo> findDueForPurge(LocalDateTime cutoff);
}
