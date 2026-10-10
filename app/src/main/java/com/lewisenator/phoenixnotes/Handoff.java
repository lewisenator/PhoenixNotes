package com.lewisenator.phoenixnotes;

import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Hands the app over from the running version (<b>old</b>) to a newly installed one (<b>new</b>),
 * without a separate launcher (see docs/decisions/0004-one-self-updating-program.md).
 *
 * <p>Old starts new as a child process, and they talk over new's stdin and stdout. This class is
 * old's side; new's side is the handoff mode of {@link Startup} ({@code --handoff}).
 *
 * <p>A successful handoff, step by step:
 *
 * <pre>
 *   OLD (running version)                       NEW (child process)
 *   ─────────────────────                       ───────────────────
 *   Handoff.handOver(jar, folder, lock, notepad) runs old's steps 1 to 4:
 * 1 Handoff.start(jar, folder)
 *     runs: java -jar versions/v/app.jar
 *           --handoff --data-dir folder   ──►   Main: Startup.in(folder, true)
 * 2                                             .awaitGo()      prints "ready",
 *   awaitReady()               ◄── "ready" ──     then waits for "go"
 * 3 notepad.saveForHandoff(), if one is open
 *   lock.release()
 *   go(window)             ── "go x y w h" ──►    reads the window position
 *                            (or just "go")       (or lets the OS place it)
 * 4                                             .lockDataFolder()   retries for up to 5s
 *                                               .openNotepad()      opens at x, y, w, h,
 *   awaitRunning()            ◄── "running" ──    then prints "running"
 * 5 exit                                        keeps running as the app
 * </pre>
 *
 * <p>Old is either a running app that found an update ({@link Update#handOff}, which also makes new
 * current before exiting), or an older install starting up ({@link Startup#handOffToCurrentVersion},
 * which has no window yet).
 *
 * <p>If new doesn't say "ready" within 30 seconds or "running" within 15, or exits first, old stops
 * it, takes the lock back, marks that version as failed, and carries on as the app. An update's
 * version never became current, so the next start still runs old.
 */
final class Handoff {

    static final String READY = "ready";
    static final String GO = "go";
    static final String RUNNING = "running";

    /** How long either side waits for the other to let go of the data folder. */
    static final Duration LOCK_WAIT = Duration.ofSeconds(5);

    private static final Duration READY_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration RUNNING_TIMEOUT = Duration.ofSeconds(15);

    private final Process process;
    private final BufferedReader output;

    private Handoff(Process process) {
        this.process = process;
        this.output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    }

    void awaitReady() throws IOException {
        await(READY, READY_TIMEOUT);
    }

    /**
     * Tells the new version to take over, and where to put its window: {@code go x y w h}, or just
     * {@code go} to let the OS place it.
     */
    void go(Optional<Rectangle> window) throws IOException {
        var input = process.getOutputStream();
        var position = window.map(w -> " %d %d %d %d".formatted(w.x, w.y, w.width, w.height))
                .orElse("");
        input.write((GO + position + "\n").getBytes(StandardCharsets.UTF_8));
        input.flush();
    }

    void awaitRunning() throws IOException {
        await(RUNNING, RUNNING_TIMEOUT);
    }

    /** Stops the new version, after a failed handoff. */
    void abandon() {
        process.destroyForcibly();
    }

    /**
     * Reads the new version's output, ignoring its log lines, until it says {@code message}. Fails if
     * it doesn't in time, or exits first.
     */
    private void await(String message, Duration timeout) throws IOException {
        var heard = CompletableFuture.supplyAsync(() -> output.lines().anyMatch(message::equals))
                .completeOnTimeout(false, timeout.toMillis(), TimeUnit.MILLISECONDS)
                .join();
        if (!heard) {
            throw new IOException("The new version didn't say " + message + " within " + timeout);
        }
    }

    /**
     * Old's whole side, steps 1 to 4: starts the version in {@code jar} and hands it the data folder.
     * Saves the notepad first, if one is open, so the new version opens with what was typed, where the
     * window was. If new doesn't take over, stops it, takes the data folder back, and throws.
     */
    static void handOver(Path jar, DataFolder folder, AppLock lock, Optional<Notepad> notepad)
            throws IOException, InterruptedException {
        var handoff = start(jar, folder);
        try {
            handoff.awaitReady();
            var window =
                    notepad.isPresent() ? Optional.of(notepad.get().saveForHandoff()) : Optional.<Rectangle>empty();
            lock.release();
            handoff.go(window);
            handoff.awaitRunning();
        } catch (IOException e) {
            handoff.abandon();
            if (!lock.take(LOCK_WAIT)) {
                throw new IllegalStateException("Couldn't take the data folder back", e);
            }
            throw e;
        }
    }

    /** Starts a version's jar on this app's Java runtime, in handoff mode. */
    static Handoff start(Path jar, DataFolder folder) throws IOException {
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var command = new ProcessBuilder(
                java,
                "-jar",
                jar.toString(),
                "--handoff",
                "--data-dir",
                folder.path().toString());
        return new Handoff(
                command.redirectError(ProcessBuilder.Redirect.INHERIT).start());
    }
}
