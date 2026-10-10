package com.lewisenator.phoenixnotes.signing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Runs external commands: op (1Password), gh (GitHub) and git. An interface, so tests can fake them. */
@FunctionalInterface
interface Shell {

    /**
     * Runs a command, writes {@code stdin} to it, and returns everything it printed. Throws if it
     * exits with an error. Secrets go in through stdin, never as arguments, which other processes
     * on the machine can see.
     */
    String run(List<String> command, String stdin) throws IOException;

    static Shell system() {
        return (command, stdin) -> {
            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (var in = process.getOutputStream()) {
                in.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            try {
                if (process.waitFor() != 0) {
                    throw new IOException(String.join(" ", command.subList(0, 2)) + " failed: " + output.strip());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while running " + command.getFirst(), e);
            }
            return output;
        };
    }
}
