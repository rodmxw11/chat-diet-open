package com.chatdiet.note;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.function.Function;

/**
 * IntentTool that saves a freeform timestamped note-to-self, for anything the user wants
 * written down that isn't food, weight, vitals, or exercise.
 */
@Component
@IntentTool(
        name = "save_note",
        intents = {"save_note"},
        description = "Save a freeform timestamped note-to-self. Use for anything the user wants written down that isn't food, weight, vitals, or exercise."
)
public class SaveNoteTool implements Function<SaveNoteRequest, ToolResult> {

    private final NoteRepository noteRepository;

    public SaveNoteTool(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    /** Saves a new timestamped {@link Note}. */
    @Override
    public ToolResult apply(SaveNoteRequest request) {
        var note = new Note(LocalDateTime.now(), request.text());
        noteRepository.save(note);
        return new ToolResult.Success("Saved note: \"" + note.text() + "\"", note.text());
    }
}
