package com.lewisenator.phoenixnotes;

import com.lewisenator.phoenixnotes.signing.KeyChain;
import com.lewisenator.phoenixnotes.signing.Release;
import com.lewisenator.phoenixnotes.signing.UntrustedException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

/**
 * The app versions installed in the data folder, and the keys they're checked against. Every trust
 * decision about what's on disk is made here; downloading just brings bytes to it.
 */
final class Installation {

    private final DataFolder folder;
    private final KeyChain builtInKeys;

    /** @param builtInKeys the key chain built into this app, trusted as is */
    Installation(DataFolder folder, KeyChain builtInKeys) {
        this.folder = folder;
        this.builtInKeys = builtInKeys;
    }

    /**
     * The built-in key chain, extended by the one saved from the last update check. The saved copy is
     * re-checked every time, so editing it can't add a key.
     */
    KeyChain trustedKeys() throws IOException {
        if (!Files.exists(folder.keys())) {
            return builtInKeys;
        }
        try {
            return builtInKeys.extend(KeyChain.read(Files.readAllBytes(folder.keys())));
        } catch (UntrustedException e) {
            return builtInKeys;
        }
    }

    /** Accepts newer generations from a published keys.json that our chain vouches for, and saves them. */
    KeyChain trustKeys(byte[] publishedKeys) throws IOException, UntrustedException {
        var trusted = trustedKeys();
        var updated = trusted.extend(KeyChain.read(publishedKeys));
        if (updated.latest().number() > trusted.latest().number()) {
            DataFolder.write(folder.keys(), updated.toJson());
        }
        return updated;
    }

    DataFolder folder() {
        return folder;
    }

    /** The version to run, if one is installed. */
    Optional<String> currentVersion() throws IOException {
        return Files.exists(folder.current())
                ? Optional.of(Files.readString(folder.current()).strip())
                : Optional.empty();
    }

    Path jar(String version) {
        return folder.version(version).resolve("app.jar");
    }

    /** A temporary file to download into, on the same disk so installing it is a rename. */
    Path newDownloadFile() throws IOException {
        Files.createDirectories(folder.path());
        return Files.createTempFile(folder.path(), "download-", ".jar");
    }

    /** Reads a downloaded release manifest, but only if it's signed by the current key. */
    Release checkRelease(byte[] manifest, String signature) throws IOException, UntrustedException {
        return Release.verify(manifest, signature, List.of(trustedKeys().currentKey()));
    }

    /**
     * Installs a downloaded release, but only if its manifest is signed by the current key and the jar
     * matches it. It doesn't become current until {@link #makeCurrent} (after a successful handoff).
     * Returns the installed version.
     */
    String install(byte[] manifest, String signature, Path downloadedJar) throws IOException, UntrustedException {
        var release = checkRelease(manifest, signature);
        release.checkJar(downloadedJar);
        var versionFolder = folder.version(release.version());
        Files.createDirectories(versionFolder);
        Files.write(versionFolder.resolve("manifest.json"), manifest);
        Files.writeString(versionFolder.resolve("manifest.json.sig"), signature);
        Files.move(downloadedJar, jar(release.version()), StandardCopyOption.REPLACE_EXISTING);
        return release.version();
    }

    /** Makes a version the one to run, with an atomic rename. */
    void makeCurrent(String version) throws IOException {
        DataFolder.write(folder.current(), version.getBytes(StandardCharsets.UTF_8));
    }

    /** Remembers that a version failed to take over, so updates don't keep retrying it. */
    void markFailed(String version) throws IOException {
        Files.writeString(folder.version(version).resolve("failed"), "");
    }

    boolean hasFailed(String version) {
        return Files.exists(folder.version(version).resolve("failed"));
    }

    /**
     * Re-checks an installed version before it runs: its manifest is signed by a key trusted for
     * installed versions, it's for this version, and the jar still matches it.
     */
    void verify(String version) throws IOException, UntrustedException {
        var versionFolder = folder.version(version);
        var release = Release.verify(
                Files.readAllBytes(versionFolder.resolve("manifest.json")),
                Files.readString(versionFolder.resolve("manifest.json.sig")),
                trustedKeys().installedVersionKeys());
        if (!release.version().equals(version)) {
            throw new UntrustedException("Manifest is for " + release.version() + ", not " + version);
        }
        release.checkJar(jar(version));
    }

    /** The installation in a data folder, trusting the key chain built into this app (trust/keys.json). */
    static Installation in(DataFolder folder) throws IOException, UntrustedException {
        try (var keys = Installation.class.getResourceAsStream("/keys.json")) {
            if (keys == null) {
                throw new IllegalStateException("Missing resource: /keys.json");
            }
            return new Installation(folder, KeyChain.read(keys.readAllBytes()));
        }
    }
}
