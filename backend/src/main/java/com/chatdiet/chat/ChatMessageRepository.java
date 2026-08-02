package com.chatdiet.chat;

import org.springframework.data.repository.ListCrudRepository;

public interface ChatMessageRepository extends ListCrudRepository<ChatMessage, Long> {
}
