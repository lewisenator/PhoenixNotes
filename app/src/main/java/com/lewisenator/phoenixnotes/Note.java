package com.lewisenator.phoenixnotes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The note's text, kept in a file. */
record Note(Path file) {

    /** The saved text, or empty if nothing has been saved yet. */
    String read() throws IOException {
        return Files.exists(file) ? Files.readString(file) : "";
    }

    /** Saves the text atomically: a crash leaves the old note or the new one, never half. */
    void write(String text) throws IOException {
        DataFolder.write(file, text.getBytes(StandardCharsets.UTF_8));
    }
}
