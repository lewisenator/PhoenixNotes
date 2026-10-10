package com.lewisenator.phoenixnotes;

import com.lewisenator.phoenixnotes.signing.UntrustedException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "phoenix-notes", description = "A notepad that keeps itself up to date.")
public final class Main implements Callable<Integer> {

    @Option(
            names = "--data-dir",
            description = "Where the note and app versions are kept (default: the OS's per-user app data folder).")
    Path dataDir;

    @Option(names = "--handoff", hidden = true, description = "Started by an older version to take over from it.")
    boolean handoff;

    @Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Show this help and exit.")
    boolean help;

    @Override
    public Integer call() throws IOException, InterruptedException, UntrustedException {
        var dataFolder = dataDir != null ? new DataFolder(dataDir) : DataFolder.forCurrentUser();
        Startup.in(dataFolder, handoff)
                .awaitGo()
                .lockDataFolder()
                .handOffToCurrentVersion()
                .openNotepad()
                .checkForUpdates();
        return 0;
    }

    public static void main(String[] args) {
        var exitCode = new CommandLine(new Main()).execute(args);
        // On success, keep running: the open window keeps the JVM alive until it's closed. If another
        // version took over, there's no window, and this one just ends.
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
