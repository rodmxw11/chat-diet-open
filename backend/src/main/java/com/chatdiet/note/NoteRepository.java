package com.chatdiet.note;

import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC repository for {@link Note}. */
public interface NoteRepository extends ListCrudRepository<Note, Long> {
}
