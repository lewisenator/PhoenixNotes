package com.lewisenator.phoenixnotes.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import com.lewisenator.phoenixnotes.DataFolder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NoteTest {

    @TempDir
    Path folder;

    @Test
    void isEmptyBeforeAnythingIsSaved() throws Exception {
        assertThat(new Note(folder.resolve("note.txt")).read()).isEmpty();
    }

    @Test
    void readsBackWhatWasWritten() throws Exception {
        var note = new Note(folder.resolve("missing/folder/note.txt"));

        note.write("first");
        note.write("second");

        assertThat(note.read()).isEqualTo("second");
    }

    @Test
    void leavesNoTemporaryFilesBehind() throws Exception {
        new Note(folder.resolve("note.txt")).write("text");

        try (var files = Files.list(folder)) {
            assertThat(files)
                    .extracting(Path::getFileName)
                    .extracting(Path::toString)
                    .containsExactly("note.txt");
        }
    }

    @Test
    void refusesAFileThatIsNotUtf8() throws Exception {
        var latin1 = Files.write(folder.resolve("old.txt"), new byte[] {'c', 'a', 'f', (byte) 0xE9});

        assertThatIOException()
                .isThrownBy(() -> new Note(latin1).read())
                .withMessage("old.txt isn't a UTF-8 text file");
    }

    @Test
    void opensTheFileOpenedLast() throws Exception {
        var data = new DataFolder(folder.resolve("data"));
        var todo = new Note(Files.writeString(folder.resolve("todo.txt"), "eggs"));

        todo.rememberIn(data);

        assertThat(Note.last(data)).isEqualTo(todo);
    }

    @Test
    void opensItsOwnNoteIfTheFileOpenedLastIsGone() throws Exception {
        var data = new DataFolder(folder.resolve("data"));
        var todo = new Note(Files.writeString(folder.resolve("todo.txt"), "eggs"));
        todo.rememberIn(data);

        Files.delete(todo.file());

        assertThat(Note.last(data)).isEqualTo(new Note(data.note()));
    }
}
