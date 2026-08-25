package com.chatdiet.note;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

/** Spring Data JDBC repository for {@link Note}. */
public interface NoteRepository extends ListCrudRepository<Note, Long> {

    /** Returns all notes, most recently logged first, for the View Notes screen. */
    @Query("SELECT * FROM note ORDER BY logged_at DESC")
    List<Note> findAllOrderByLoggedAtDesc();
}
