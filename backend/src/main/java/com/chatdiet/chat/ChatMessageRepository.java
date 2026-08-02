package com.chatdiet.chat;

import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC repository for the durable {@link ChatMessage} audit log. */
public interface ChatMessageRepository extends ListCrudRepository<ChatMessage, Long> {
}
