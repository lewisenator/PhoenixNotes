package com.lewisenator.phoenixnotes.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.lewisenator.phoenixnotes.DataFolder;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Opens a real window, so it needs a display (CI uses a virtual one on Linux). */
class NotepadTest {

    @TempDir
    Path folder;

    private DataFolder data;
    private Note note;
    private Notepad notepad;

    @BeforeEach
    void open() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        data = new DataFolder(folder.resolve("data"));
        note = new Note(data.note());
        note.write("hello");
        notepad = Notepad.open(data, "1.0.42", Optional.empty());
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

    @Test
    void coversTheWindowAndStopsTypingWhileUpdating() throws Exception {
        notepad.showUpdating("1.0.43");

        SwingUtilities.invokeAndWait(() -> {
            assertThat(notepad.frame().getGlassPane().isVisible()).isTrue();
            assertThat(notepad.overlay().message().getText()).isEqualTo("Updating to 1.0.43…");
            assertThat(notepad.text().isEditable()).isFalse();
        });
    }

    @Test
    void showsAMessageAndLetsTypingCarryOn() throws Exception {
        notepad.showUpdating("1.0.43");
        notepad.showBriefly("Update to 1.0.43 failed — still on 1.0.42");

        SwingUtilities.invokeAndWait(() -> {
            assertThat(notepad.overlay().isVisible()).isTrue();
            assertThat(notepad.overlay().message().getText()).isEqualTo("Update to 1.0.43 failed — still on 1.0.42");
            assertThat(notepad.text().isEditable()).isTrue();
        });
    }

    @Test
    void opensATextFileAndSavesBackToIt() throws Exception {
        var todo = Files.writeString(folder.resolve("todo.txt"), "eggs");

        SwingUtilities.invokeAndWait(() -> {
            notepad.text().setText("hello, edited");
            notepad.open(todo);
            assertThat(notepad.frame().getTitle()).isEqualTo("todo.txt — Phoenix Notes 1.0.42");
            assertThat(notepad.text().getText()).isEqualTo("eggs");
            notepad.text().setText("eggs, coffee");
            notepad.save();
        });

        assertThat(todo).hasContent("eggs, coffee");
        assertThat(note.read()).as("saved before switching").isEqualTo("hello, edited");
    }

    @Test
    void reopensTheFileOpenedLast() throws Exception {
        var todo = Files.writeString(folder.resolve("todo.txt"), "eggs");
        SwingUtilities.invokeAndWait(() -> notepad.open(todo));
        close();

        notepad = Notepad.open(data, "1.0.42", Optional.empty());

        SwingUtilities.invokeAndWait(() -> assertThat(notepad.text().getText()).isEqualTo("eggs"));
    }

    @Test
    void doesNotOpenAFileThatIsNotUtf8() throws Exception {
        var latin1 = Files.write(folder.resolve("old.txt"), new byte[] {'c', 'a', 'f', (byte) 0xE9});

        SwingUtilities.invokeAndWait(() -> {
            notepad.open(latin1);
            assertThat(notepad.overlay().message().getText()).isEqualTo("Couldn't open old.txt");
            assertThat(notepad.status().getText()).endsWith("old.txt isn't a UTF-8 text file");
            assertThat(notepad.text().getText()).isEqualTo("hello");
        });
    }

    @Test
    void undoesTypingButNotOpening() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertThat(notepad.undo().canUndo()).as("loading isn't an edit").isFalse();
            notepad.text().append(" world");
            notepad.undo().undo();
            assertThat(notepad.text().getText()).isEqualTo("hello");
        });
    }

    @Test
    void hasTheUsualShortcuts() throws Exception {
        var menuKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        SwingUtilities.invokeAndWait(() -> {
            var file = notepad.frame().getJMenuBar().getMenu(0);
            assertThat(file.getText()).isEqualTo("File");
            assertThat(file.getItem(0).getAccelerator()).isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_O, menuKey));
            assertThat(file.getItem(1).getAccelerator()).isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuKey));
        });
    }
}
