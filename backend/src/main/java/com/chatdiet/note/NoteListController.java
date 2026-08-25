package com.chatdiet.note;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** REST surface for the read-only View Notes screen. */
@RestController
public class NoteListController {

    private final NoteRepository noteRepository;

    public NoteListController(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    /** Returns every note, most recently logged first. */
    @GetMapping("/api/notes")
    public List<Note> list() {
        return noteRepository.findAllOrderByLoggedAtDesc();
    }
}
