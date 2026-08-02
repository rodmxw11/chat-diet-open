package com.chatdiet.chat;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;

public interface ChatSessionRepository extends ListCrudRepository<ChatSession, Long> {

    @Query("SELECT * FROM chat_session WHERE session_key = :sessionKey")
    Optional<ChatSession> findBySessionKey(String sessionKey);
}
