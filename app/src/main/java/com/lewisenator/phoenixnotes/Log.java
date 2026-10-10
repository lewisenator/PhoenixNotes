package com.lewisenator.phoenixnotes;

/** One line per workflow step, so the {@link Startup} and {@link Update} logs read like their code. */
final class Log {

    private Log() {}

    static void step(String step, String outcome) {
        System.out.printf("%-20s %s%n", step, outcome);
    }
}
