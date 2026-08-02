package com.chatdiet.requirement;

import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC repository for {@link RequirementEntry}. */
public interface RequirementEntryRepository extends ListCrudRepository<RequirementEntry, Long> {
}
