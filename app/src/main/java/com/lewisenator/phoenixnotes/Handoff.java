package com.lewisenator.phoenixnotes;

import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
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
 * 1 Handoff.start(jar, folder)
 *     runs: java -jar versions/v/app.jar
 *           --handoff --data-dir folder   ──►   Main: Startup.in(folder, true)
 * 2                                             .awaitGo()      prints "ready",
 *   awaitReady()               ◄── "ready" ──     then waits for "go"
 * 3 save the note, note the window position,
 *   release the data-folder lock
 *   go(window)             ── "go x y w h" ──►    reads the window position
 * 4                                             .lockDataFolder()   retries for up to 5s
 *                                               .openNotepad()      opens at x, y, w, h,
 *   awaitRunning()            ◄── "running" ──    then prints "running"
 * 5 Installation.makeCurrent(new version)
 *   exit                                        keeps running as the app
 * </pre>
 *
 * <p>Old's steps 3 and 5 belong to whoever drives the handoff (the {@code Update} workflow); this
 * class does the starting, talking and timing.
 *
 * <p>If new doesn't say "ready" within 30 seconds or "running" within 15, or exits first, old
 * calls {@link #abandon()} to stop it, takes the lock back, and carries on as the app. The new
 * version never became current, so the next start still runs old.
 */
final class Handoff {

    static final String READY = "ready";
    static final String GO = "go";
    static final String RUNNING = "running";

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

    /** Tells the new version to take over, and where to put its window. */
    void go(Rectangle window) throws IOException {
        var input = process.getOutputStream();
        input.write("%s %d %d %d %d%n"
                .formatted(GO, window.x, window.y, window.width, window.height)
                .getBytes(StandardCharsets.UTF_8));
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
