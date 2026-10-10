package com.lewisenator.phoenixnotes;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Optional;
import java.util.Scanner;

/**
 * What happens when the app starts, step by step. Each step logs one line, and a step that doesn't
 * apply skips itself, so the chain in {@link Main} always reads top to bottom.
 */
final class Startup {

    /** The version of a build run from source, which doesn't update itself. */
    static final String DEV = "dev";

    private final DataFolder dataFolder;
    private final boolean handingOver;
    private final AppLock lock;
    private boolean alreadyRunning;
    private Optional<Rectangle> window = Optional.empty();

    private Startup(DataFolder dataFolder, boolean handingOver) {
        this.dataFolder = dataFolder;
        this.handingOver = handingOver;
        this.lock = new AppLock(dataFolder.lock());
    }

    /**
     * When an older version started us to take over (see {@link Handoff}): say we're ready, then wait
     * for "go" and the old window's position. Otherwise, skipped.
     */
    Startup awaitGo() throws IOException {
        if (!handingOver) {
            return this;
        }
        System.out.println(Handoff.READY);
        var in = new Scanner(System.in, StandardCharsets.UTF_8);
        if (!in.hasNext() || !in.next().equals(Handoff.GO)) {
            throw new IOException("The previous version didn't say go");
        }
        window = Optional.of(new Rectangle(in.nextInt(), in.nextInt(), in.nextInt(), in.nextInt()));
        Log.step("await go", "taking over from the previous version");
        return this;
    }

    /**
     * Only one copy of the app may use a data folder. A second copy notes that and skips the rest.
     * During a handoff, waits briefly for the old version to let go of it.
     */
    Startup lockDataFolder() throws IOException, InterruptedException {
        Files.createDirectories(dataFolder.path());
        if (!lock.take(handingOver ? Handoff.LOCK_WAIT : Duration.ZERO)) {
            alreadyRunning = true;
            Log.step("lock data folder", "Phoenix Notes is already running here; nothing to do");
            return this;
        }
        Log.step("lock data folder", "ok");
        return this;
    }

    /** Opens the notepad, unless another copy of the app is already running. */
    Optional<Notepad> openNotepad() throws IOException {
        if (alreadyRunning) {
            return Optional.empty();
        }
        var notepad = Notepad.open(new Note(dataFolder.note()), version(), window);
        Log.step("open notepad", "ok");
        if (handingOver) {
            System.out.println(Handoff.RUNNING);
        }
        return Optional.of(notepad);
    }

    boolean alreadyRunning() {
        return alreadyRunning;
    }

    /** @param handingOver whether an older version started this one to take over from it */
    static Startup in(DataFolder dataFolder, boolean handingOver) {
        Log.step("start", "Phoenix Notes " + version() + ", data in " + dataFolder.path());
        return new Startup(dataFolder, handingOver);
    }

    /** This app's version, from its jar's manifest, or "dev" when running from source. */
    static String version() {
        var version = Startup.class.getPackage().getImplementationVersion();
        return version != null ? version : DEV;
    }
}
