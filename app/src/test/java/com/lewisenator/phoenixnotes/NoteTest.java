package com.lewisenator.phoenixnotes;

import static org.assertj.core.api.Assertions.assertThat;

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
}
