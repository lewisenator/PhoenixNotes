package com.lewisenator.phoenixnotes;

import java.nio.file.Path;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "phoenix-notes", description = "A notepad that keeps itself up to date.")
public final class Main implements Runnable {

    @Option(
            names = "--data-dir",
            description = "Where the note and app versions are kept (default: the OS's per-user app data folder).")
    Path dataDir;

    @Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Show this help and exit.")
    boolean help;

    @Override
    public void run() {
        var dataFolder = dataDir != null ? new DataFolder(dataDir) : DataFolder.forCurrentUser();
        Startup.in(dataFolder).lockDataFolder().openNotepad();
    }

    public static void main(String[] args) {
        var exitCode = new CommandLine(new Main()).execute(args);
        // On success, keep running: the open window keeps the JVM alive until it's closed.
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
