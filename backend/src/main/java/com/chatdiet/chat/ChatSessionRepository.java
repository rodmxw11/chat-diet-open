package com.chatdiet.chat;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;

/** Spring Data JDBC repository for the durable {@link ChatSession} record. */
public interface ChatSessionRepository extends ListCrudRepository<ChatSession, Long> {

    /** Looks up the session tracking a given conversation key, if one has been started. */
    @Query("SELECT * FROM chat_session WHERE session_key = :sessionKey")
    Optional<ChatSession> findBySessionKey(String sessionKey);
}
