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
 * The old version's side of handing over to a new one (see docs/decisions/0004). The new version
 * runs as a child process, and they talk over its stdin and stdout:
 *
 * <pre>
 * new → "ready"          it has started
 * old → "go x y w h"     old has saved the note and released the data folder; its window was here
 * new → "running"        new has taken the data folder and opened its window there
 * </pre>
 *
 * If an answer doesn't come in time, or the new version exits, the old one stops it and carries on.
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
