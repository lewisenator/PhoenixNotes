package com.lewisenator.phoenixnotes;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Scanner;

/**
 * What happens when the app starts, step by step. Each step logs one line, and a step that doesn't
 * apply skips itself, so the chain in {@link Main} always reads top to bottom.
 */
final class Startup {

    /** How long a new version waits for the old one to release the data folder during a handoff. */
    private static final Duration LOCK_WAIT = Duration.ofSeconds(5);

    private final DataFolder dataFolder;
    private final boolean handingOver;
    private boolean alreadyRunning;
    private Optional<Rectangle> window = Optional.empty();

    /** Kept open, and so locked, for as long as the app runs. The OS releases it if the app dies. */
    @SuppressWarnings("UnusedVariable") // Never read: holding it open is the point.
    private FileChannel lock;

    private Startup(DataFolder dataFolder, boolean handingOver) {
        this.dataFolder = dataFolder;
        this.handingOver = handingOver;
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
        log("await go", "taking over from the previous version");
        return this;
    }

    /**
     * Only one copy of the app may use a data folder. A second copy notes that and skips the rest.
     * During a handoff, waits briefly for the old version to let go of it.
     */
    Startup lockDataFolder() throws IOException, InterruptedException {
        Files.createDirectories(dataFolder.path());
        var channel = FileChannel.open(dataFolder.lock(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        var giveUpAt = Instant.now().plus(handingOver ? LOCK_WAIT : Duration.ZERO);
        while (!tryLock(channel)) {
            if (Instant.now().isAfter(giveUpAt)) {
                channel.close();
                alreadyRunning = true;
                log("lock data folder", "Phoenix Notes is already running here; nothing to do");
                return this;
            }
            Thread.sleep(Duration.ofMillis(50));
        }
        lock = channel;
        log("lock data folder", "ok");
        return this;
    }

    /** Opens the notepad, unless another copy of the app is already running. */
    Optional<Notepad> openNotepad() throws IOException {
        if (alreadyRunning) {
            return Optional.empty();
        }
        var notepad = Notepad.open(new Note(dataFolder.note()), version(), window);
        log("open notepad", "ok");
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
        log("start", "Phoenix Notes " + version() + ", data in " + dataFolder.path());
        return new Startup(dataFolder, handingOver);
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
