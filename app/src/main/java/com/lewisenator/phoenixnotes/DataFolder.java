package com.lewisenator.phoenixnotes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The per-user folder where the app keeps its files. Every file it uses is listed here:
 *
 * <pre>
 * note.txt                       the note
 * app.lock                       held while the app runs
 * keys.json                      the newest trusted key chain
 * current                        the version to run, e.g. "1.0.16"
 * versions/1.0.16/app.jar        each version's jar, with its signed manifest:
 * versions/1.0.16/manifest.json
 * versions/1.0.16/manifest.json.sig
 * </pre>
 */
record DataFolder(Path path) {

    Path note() {
        return path.resolve("note.txt");
    }

    Path lock() {
        return path.resolve("app.lock");
    }

    Path keys() {
        return path.resolve("keys.json");
    }

    Path current() {
        return path.resolve("current");
    }

    Path version(String version) {
        return path.resolve("versions").resolve(version);
    }

    /**
     * Writes a file next to the old one, then swaps it in with an atomic rename, so a crash leaves
     * either the old contents or the new ones, never half of either.
     */
    static void write(Path file, byte[] contents) throws IOException {
        var folder = file.toAbsolutePath().getParent();
        Files.createDirectories(folder);
        var temp = Files.createTempFile(folder, file.getFileName() + "-", ".tmp");
        Files.write(temp, contents);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
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
