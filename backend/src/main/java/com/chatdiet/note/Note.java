package com.chatdiet.note;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime loggedAt;

    private String text;

    protected Note() {
    }

    public Note(LocalDateTime loggedAt, String text) {
        this.loggedAt = loggedAt;
        this.text = text;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getLoggedAt() {
        return loggedAt;
    }

    public String getText() {
        return text;
    }
}
