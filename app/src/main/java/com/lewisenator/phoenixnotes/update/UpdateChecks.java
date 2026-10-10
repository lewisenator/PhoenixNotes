package com.lewisenator.phoenixnotes.update;

import com.lewisenator.phoenixnotes.AppLock;
import com.lewisenator.phoenixnotes.Log;
import com.lewisenator.phoenixnotes.ui.Notepad;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * When to check for updates: as the app starts, every {@link #CHECK_INTERVAL}, and when "Check for
 * updates" is clicked. Checks run one at a time on a background thread, so the window never waits
 * on the network, and updates apply as soon as they're found.
 */
public final class UpdateChecks {

    static final Duration CHECK_INTERVAL = Duration.ofMinutes(15);

    /** A daemon thread, so it never keeps the app running once the window is closed. */
    private final ScheduledExecutorService thread = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().factory());

    private final Installation installation;
    private final Download download;
    private final String version;
    private final Notepad notepad;
    private final AppLock lock;
    private final Runnable exit;

    /** @param exit ends this process once a new version is running */
    public UpdateChecks(
            Installation installation,
            Download download,
            String version,
            Notepad notepad,
            AppLock lock,
            Runnable exit) {
        this.installation = installation;
        this.download = download;
        this.version = version;
        this.notepad = notepad;
        this.lock = lock;
        this.exit = exit;
    }

    public void start() {
        notepad.onCheckForUpdates(() -> thread.execute(this::check));
        // Nothing to wait for: check() handles its own failures.
        var _ = thread.scheduleWithFixedDelay(this::check, 0, CHECK_INTERVAL.toSeconds(), TimeUnit.SECONDS);
    }

    /**
     * One check, start to finish. Catches everything: a scheduled task that throws is never run again,
     * and one failed check (no network, say) shouldn't stop the next.
     */
    void check() {
        notepad.showCheck("Checking…", false);
        try {
            var update = Update.check(installation, download, version)
                    .fetchLatestKeys()
                    .fetchLatestRelease()
                    .skipUnlessNewer()
                    .downloadAndInstall()
                    .handOff(notepad, lock, exit);
            notepad.showCheck(capitalize(update.outcome()), true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Log.step("check for updates", "couldn't: " + e.getMessage());
            notepad.showCheck("Couldn't check for updates", true);
        }
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
