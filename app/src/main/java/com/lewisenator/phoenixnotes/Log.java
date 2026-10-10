package com.lewisenator.phoenixnotes;

/** One line per workflow step, so the {@link Startup} and {@code Update} logs read like their code. */
public final class Log {

    private Log() {}

    public static void step(String step, String outcome) {
        System.out.printf("%-20s %s%n", step, outcome);
    }
}
