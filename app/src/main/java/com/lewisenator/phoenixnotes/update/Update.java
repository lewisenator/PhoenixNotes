package com.lewisenator.phoenixnotes.update;

import com.lewisenator.phoenixnotes.AppLock;
import com.lewisenator.phoenixnotes.Log;
import com.lewisenator.phoenixnotes.Startup;
import com.lewisenator.phoenixnotes.signing.Release;
import com.lewisenator.phoenixnotes.signing.UntrustedException;
import com.lewisenator.phoenixnotes.ui.Notepad;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * One check for updates, step by step, like {@link Startup}: each step logs one line, and once
 * there's nothing to update to, the rest skip themselves.
 *
 * <pre>
 * Update.check(installation, download, runningVersion)
 *         .fetchLatestKeys()
 *         .fetchLatestRelease()
 *         .skipUnlessNewer()
 *         .downloadAndInstall()
 *         .handOff(notepad, lock, exit);
 * </pre>
 *
 * A step that can't reach the release, or finds something untrusted, throws, and nothing changes.
 */
final class Update {

    private final Installation installation;
    private final Download download;
    private final String runningVersion;
    private byte[] manifest = new byte[0];
    private String signature = "";

    /** The release to update to, until a step finds there's nothing to do. */
    private Optional<Release> release = Optional.empty();

    private String outcome = "";

    private Update(Installation installation, Download download, String runningVersion) {
        this.installation = installation;
        this.download = download;
        this.runningVersion = runningVersion;
    }

    /**
     * Keys first, so a release signed by a newly rotated key is trusted (see
     * docs/decisions/0009-key-rotation.md).
     */
    Update fetchLatestKeys() throws IOException, InterruptedException, UntrustedException {
        var keys = installation.trustKeys(download.bytes("keys.json"));
        Log.step("fetch latest keys", "generation " + keys.latest().number());
        return this;
    }

    /** Reads the latest release's manifest, which is only readable once its signature checks out. */
    Update fetchLatestRelease() throws IOException, InterruptedException, UntrustedException {
        manifest = download.bytes("manifest.json");
        signature = new String(download.bytes("manifest.json.sig"), StandardCharsets.UTF_8);
        var latest = installation.checkRelease(manifest, signature);
        release = Optional.of(latest);
        Log.step("fetch latest release", latest.version());
        return this;
    }

    Update skipUnlessNewer() {
        var latest = release.orElseThrow();
        if (runningVersion.equals(Startup.DEV)) {
            return skip("dev builds don't update");
        }
        if (!latest.isNewerThan(runningVersion)) {
            return skip("up to date");
        }
        if (installation.hasFailed(latest.version())) {
            return skip(latest.version() + " failed to take over before");
        }
        Log.step("skip unless newer", "updating to " + latest.version());
        return this;
    }

    /** Downloads the jar, and installs it only if it matches the signed manifest. */
    Update downloadAndInstall() throws IOException, InterruptedException, UntrustedException {
        if (release.isEmpty()) {
            return this;
        }
        var jar = installation.newDownloadFile();
        download.toFile(release.get().url(), jar);
        Log.step("download, install", installation.install(manifest, signature, jar));
        return this;
    }

    /**
     * Hands the app over to the installed version, as old in {@link Handoff}'s sequence. If the new
     * version doesn't take over, stops it, takes the data folder back and keeps running, and marks the
     * version as failed so later checks skip it.
     *
     * @param exit ends this process once the new version is running
     */
    Update handOff(Notepad notepad, AppLock lock, Runnable exit) throws IOException, InterruptedException {
        if (release.isEmpty()) {
            return this;
        }
        var version = release.get().version();
        notepad.showUpdating(version);
        try {
            Handoff.handOver(installation.jar(version), installation.folder(), lock, Optional.of(notepad));
        } catch (IOException e) {
            notepad.showBriefly("Update to " + version + " failed — still on " + runningVersion);
            outcome = "update to " + version + " failed";
            installation.markFailed(version);
            Log.step("hand off", "failed, keeping " + runningVersion + ": " + e.getMessage());
            return this;
        }
        installation.makeCurrent(version);
        Log.step("hand off", version + " is running; exiting");
        exit.run();
        return this;
    }

    /** Why this check didn't update, for the "Check for updates" button: "up to date", say. */
    String outcome() {
        return outcome;
    }

    private Update skip(String why) {
        release = Optional.empty();
        outcome = why;
        Log.step("skip unless newer", why);
        return this;
    }

    static Update check(Installation installation, Download download, String runningVersion) {
        Log.step("check for updates", "running " + runningVersion);
        return new Update(installation, download, runningVersion);
    }
}
