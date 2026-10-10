package com.lewisenator.phoenixnotes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** The note's text, kept in a file. */
record Note(Path file) {

    /** The saved text, or empty if nothing has been saved yet. */
    String read() throws IOException {
        return Files.exists(file) ? Files.readString(file) : "";
    }

    /**
     * Saves the text. It's written next to the old file, then swapped in with an atomic rename, so a
     * crash leaves either the old note or the new one, never half of it.
     */
    void write(String text) throws IOException {
        var folder = file.toAbsolutePath().getParent();
        Files.createDirectories(folder);
        var temp = Files.createTempFile(folder, "note-", ".tmp");
        Files.writeString(temp, text);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
