package com.chatdiet.note;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class SaveNoteTool {

    private final NoteRepository noteRepository;

    public SaveNoteTool(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    @Tool(name = "save_note", description = "Save a freeform timestamped note-to-self. Use for anything the user wants written down that isn't food, weight, vitals, or exercise.")
    public String saveNote(@ToolParam(description = "The note text, verbatim as the user said it") String text) {
        var note = new Note(LocalDateTime.now(), text);
        noteRepository.save(note);
        return "Saved note: \"" + note.getText() + "\"";
    }
}
