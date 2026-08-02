package com.chatdiet.digestive;

import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC repository for {@link DigestiveEvent}. */
public interface DigestiveEventRepository extends ListCrudRepository<DigestiveEvent, Long> {
}
