package com.lewisenator.phoenixnotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Opens a real window, so it needs a display (CI uses a virtual one on Linux). */
class NotepadTest {

    @TempDir
    Path folder;

    private Note note;
    private Notepad notepad;

    @BeforeEach
    void open() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        note = new Note(folder.resolve("note.txt"));
        note.write("hello");
        notepad = Notepad.open(note, "1.0.42");
    }

    @AfterEach
    void close() throws Exception {
        if (notepad != null) {
            SwingUtilities.invokeAndWait(() -> notepad.frame().dispose());
        }
    }

    @Test
    void showsTheVersionAndTheSavedNote() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertThat(notepad.frame().getTitle()).isEqualTo("Phoenix Notes 1.0.42");
            assertThat(notepad.status().getText()).startsWith("Version 1.0.42");
            assertThat(notepad.text().getText()).isEqualTo("hello");
        });
    }

    @Test
    void savesWhatWasTyped() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            notepad.text().setText("typed");
            notepad.save();
        });

        assertThat(note.read()).isEqualTo("typed");
    }

    @Test
    void savesWhenTheWindowCloses() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            notepad.text().setText("closing");
            var frame = notepad.frame();
            frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
        });

        assertThat(note.read()).isEqualTo("closing");
    }

    @Test
    void showsWhenSavingFails() throws Exception {
        // A folder where the note file should be makes writing fail.
        Files.delete(note.file());
        Files.createDirectory(note.file());

        SwingUtilities.invokeAndWait(() -> {
            notepad.save();
            assertThat(notepad.status().getText()).contains("Couldn't save");
        });
    }
}
