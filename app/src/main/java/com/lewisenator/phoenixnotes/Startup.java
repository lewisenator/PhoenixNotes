package com.lewisenator.phoenixnotes;

import com.lewisenator.phoenixnotes.signing.UntrustedException;
import com.lewisenator.phoenixnotes.ui.Notepad;
import com.lewisenator.phoenixnotes.update.Download;
import com.lewisenator.phoenixnotes.update.Handoff;
import com.lewisenator.phoenixnotes.update.Installation;
import com.lewisenator.phoenixnotes.update.UpdateChecks;
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
public final class Startup {

    /** The version of a build run from source, which doesn't update itself. */
    public static final String DEV = "dev";

    private final Installation installation;
    private final DataFolder dataFolder;
    private final boolean handingOver;
    private final String version;
    private final AppLock lock;
    private boolean alreadyRunning;
    private boolean handedOff;
    private Optional<Rectangle> window = Optional.empty();
    private Optional<Notepad> notepad = Optional.empty();

    private Startup(Installation installation, boolean handingOver, String version) {
        this.installation = installation;
        this.dataFolder = installation.folder();
        this.handingOver = handingOver;
        this.version = version;
        this.lock = new AppLock(dataFolder.lock());
    }

    /**
     * When an older version started us to take over (see {@link Handoff}): say we're ready, then wait
     * for "go" and, if it had a window, its position. Otherwise, skipped.
     */
    Startup awaitGo() throws IOException {
        if (!handingOver) {
            return this;
        }
        System.out.println(Handoff.READY);
        var in = new Scanner(System.in, StandardCharsets.UTF_8);
        var go = new Scanner(in.hasNextLine() ? in.nextLine() : "");
        if (!go.hasNext() || !go.next().equals(Handoff.GO)) {
            throw new IOException("The previous version didn't say go");
        }
        if (go.hasNextInt()) {
            window = Optional.of(new Rectangle(go.nextInt(), go.nextInt(), go.nextInt(), go.nextInt()));
        }
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

    /**
     * Runs the newest version instead of this one, if one is installed: an old installer still starts
     * the version that last updated it. That version is re-verified first, and if it doesn't verify or
     * doesn't take over, we carry on as ourselves. Skipped during a handoff.
     */
    Startup handOffToCurrentVersion() throws IOException, InterruptedException {
        if (handingOver || alreadyRunning) {
            return this;
        }
        var current = installation.currentVersion();
        if (version.equals(DEV) || current.isEmpty() || installation.hasFailed(current.get())) {
            Log.step("hand off to current", "nothing newer installed");
            return this;
        }
        try {
            if (!installation.verify(current.get()).isNewerThan(version)) {
                Log.step("hand off to current", "this is the newest");
                return this;
            }
            Handoff.handOver(installation.jar(current.get()), dataFolder, lock, Optional.empty());
        } catch (UntrustedException e) {
            Log.step("hand off to current", current.get() + " doesn't verify: " + e.getMessage());
            return this;
        } catch (IOException e) {
            installation.markFailed(current.get());
            Log.step("hand off to current", current.get() + " failed: " + e.getMessage());
            return this;
        }
        handedOff = true;
        Log.step("hand off to current", current.get() + " is running; exiting");
        return this;
    }

    /** Opens the notepad, unless another copy of the app is running, or another version took over. */
    Startup openNotepad() throws IOException {
        if (alreadyRunning || handedOff) {
            return this;
        }
        var opened = Notepad.open(dataFolder, version, window);
        notepad = Optional.of(opened);
        Log.step("open notepad", "ok");
        if (handingOver) {
            opened.showBriefly("Updated to " + version);
            System.out.println(Handoff.RUNNING);
        }
        return this;
    }

    /** Checks for updates now and every so often, once the notepad is open (see {@link UpdateChecks}). */
    Startup checkForUpdates() {
        notepad.ifPresent(opened -> new UpdateChecks(
                        installation,
                        new Download(Download.LATEST_RELEASE),
                        version,
                        opened,
                        lock,
                        () -> System.exit(0))
                .start());
        return this;
    }

    boolean alreadyRunning() {
        return alreadyRunning;
    }

    boolean handedOff() {
        return handedOff;
    }

    Optional<Notepad> notepad() {
        return notepad;
    }

    /** @param handingOver whether an older version started this one to take over from it */
    static Startup in(DataFolder dataFolder, boolean handingOver) throws IOException, UntrustedException {
        return in(Installation.in(dataFolder), handingOver, version());
    }

    /** @param version this app's version; tests pass one, as if running an installed release */
    static Startup in(Installation installation, boolean handingOver, String version) {
        Log.step(
                "start",
                "Phoenix Notes " + version + ", data in "
                        + installation.folder().path());
        return new Startup(installation, handingOver, version);
    }

    /** This app's version, from its jar's manifest, or "dev" when running from source. */
    static String version() {
        var version = Startup.class.getPackage().getImplementationVersion();
        return version != null ? version : DEV;
    }
}
