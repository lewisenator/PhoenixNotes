package com.lewisenator.phoenixnotes;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * What happens when the app starts, step by step. Each step logs one line, and a step that doesn't
 * apply skips itself, so the chain in {@link Main} always reads top to bottom.
 */
final class Startup {

    private final DataFolder dataFolder;
    private boolean alreadyRunning;

    /** Kept open, and so locked, for as long as the app runs. The OS releases it if the app dies. */
    @SuppressWarnings("UnusedVariable") // Never read: holding it open is the point.
    private FileChannel lock;

    private Startup(DataFolder dataFolder) {
        this.dataFolder = dataFolder;
    }

    /** Only one copy of the app may use a data folder. A second copy notes that and skips the rest. */
    Startup lockDataFolder() throws IOException {
        Files.createDirectories(dataFolder.path());
        var channel = FileChannel.open(dataFolder.lock(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        if (tryLock(channel)) {
            lock = channel;
            log("lock data folder", "ok");
        } else {
            channel.close();
            alreadyRunning = true;
            log("lock data folder", "Phoenix Notes is already running here; nothing to do");
        }
        return this;
    }

    /** Opens the notepad, unless another copy of the app is already running. */
    Optional<Notepad> openNotepad() throws IOException {
        if (alreadyRunning) {
            return Optional.empty();
        }
        var notepad = Notepad.open(new Note(dataFolder.note()), version());
        log("open notepad", "ok");
        return Optional.of(notepad);
    }

    boolean alreadyRunning() {
        return alreadyRunning;
    }

    static Startup in(DataFolder dataFolder) {
        log("start", "Phoenix Notes " + version() + ", data in " + dataFolder.path());
        return new Startup(dataFolder);
    }

    /** This app's version, from its jar's manifest, or "dev" when running from source. */
    static String version() {
        var version = Startup.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    /** True if we got the lock; false if another process (or this one) already holds it. */
    private static boolean tryLock(FileChannel channel) throws IOException {
        try {
            return channel.tryLock() != null;
        } catch (OverlappingFileLockException e) {
            return false;
        }
    }

    private static void log(String step, String outcome) {
        System.out.printf("%-18s %s%n", step, outcome);
    }
}
