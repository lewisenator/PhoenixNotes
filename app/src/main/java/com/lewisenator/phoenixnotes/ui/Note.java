package com.lewisenator.phoenixnotes.ui;

import com.lewisenator.phoenixnotes.DataFolder;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** A note's text, kept in a file: the app's own note.txt, or any UTF-8 text file the user opens. */
public record Note(Path file) {

    /** The saved text, or empty if nothing has been saved yet. Refuses files that aren't UTF-8. */
    public String read() throws IOException {
        try {
            return Files.exists(file) ? Files.readString(file) : "";
        } catch (CharacterCodingException e) {
            // Guessing another encoding could garble the file when it's saved, so don't open it at all.
            throw new IOException(file.getFileName() + " isn't a UTF-8 text file", e);
        }
    }

    /** Saves the text atomically: a crash leaves the old note or the new one, never half. */
    public void write(String text) throws IOException {
        DataFolder.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    /** Remembers this as the note to open next time, and in a version that takes over. */
    void rememberIn(DataFolder folder) throws IOException {
        DataFolder.write(folder.openFile(), file.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The note to open: the file opened last, if it's still there and still UTF-8 text, or the app's
     * own note.
     */
    static Note last(DataFolder folder) throws IOException {
        if (Files.exists(folder.openFile())) {
            var last = new Note(Path.of(Files.readString(folder.openFile()).strip()));
            if (Files.isRegularFile(last.file()) && last.isText()) {
                return last;
            }
        }
        return new Note(folder.note());
    }

    private boolean isText() {
        try {
            read();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
