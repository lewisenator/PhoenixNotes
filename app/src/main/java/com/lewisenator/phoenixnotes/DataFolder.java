package com.lewisenator.phoenixnotes;

import java.nio.file.Path;

/** The per-user folder where the app keeps its files. Every file it uses is listed here. */
record DataFolder(Path path) {

    /** The note's text. */
    Path note() {
        return path.resolve("note.txt");
    }

    /** Held while the app runs, so only one copy uses this folder at a time. */
    Path lock() {
        return path.resolve("app.lock");
    }

    /** The OS's standard folder for per-user app data. */
    static DataFolder forCurrentUser() {
        var os = System.getProperty("os.name");
        var home = Path.of(System.getProperty("user.home"));
        if (os.startsWith("Mac")) {
            return new DataFolder(home.resolve("Library/Application Support/PhoenixNotes"));
        }
        if (os.startsWith("Windows")) {
            return new DataFolder(Path.of(System.getenv("LOCALAPPDATA"), "PhoenixNotes"));
        }
        var xdgDataHome = System.getenv("XDG_DATA_HOME");
        var dataHome =
                xdgDataHome == null || xdgDataHome.isBlank() ? home.resolve(".local/share") : Path.of(xdgDataHome);
        return new DataFolder(dataHome.resolve("phoenixnotes"));
    }
}
