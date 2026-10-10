package com.lewisenator.phoenixnotes.signing;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Collection;
import java.util.HexFormat;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * One release of the app, as described by its signed manifest: the version, where to download its
 * jar, and the size and SHA-256 hash the jar must have.
 *
 * <p>The only way to turn downloaded bytes into a {@code Release} is {@link #verify}, which checks
 * the signature before reading anything.
 */
public record Release(String version, URI url, String sha256, long size) {

    private static final Pattern VERSION = Pattern.compile("\\d+(\\.\\d+)*");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    static final JsonMapper JSON = JsonMapper.builder()
            // Newer releases may add fields; older apps ignore what they don't know.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public Release {
        // The version becomes a folder name, so only digits and dots.
        require(version != null && VERSION.matcher(version).matches(), "version must be numbers and dots");
        require(url != null && url.isAbsolute(), "url must be absolute");
        require(sha256 != null && SHA256.matcher(sha256).matches(), "sha256 must be 64 lowercase hex digits");
        require(size > 0, "size must be positive");
    }

    /** True if this release is newer than the given version, comparing the number after the last dot. */
    public boolean isNewerThan(String otherVersion) {
        return buildNumber(version) > buildNumber(otherVersion);
    }

    /** Throws unless the jar has exactly the size and SHA-256 hash this release promises. */
    public void checkJar(Path jar) throws IOException, UntrustedException {
        if (Files.size(jar) != size || !sha256Of(jar).equals(sha256)) {
            throw new UntrustedException("Jar doesn't match the release");
        }
    }

    /** The manifest's JSON: the exact bytes that get signed. */
    public byte[] toJson() {
        return JSON.writeValueAsBytes(this);
    }

    /** Describes a jar for a new release. */
    public static Release of(Path jar, String version, URI url) throws IOException {
        return new Release(version, url, sha256Of(jar), Files.size(jar));
    }

    /**
     * Checks that one of the keys signed exactly these bytes, and only then parses them. Nothing
     * from an unsigned or tampered manifest is ever read.
     */
    public static Release verify(byte[] manifest, String signature, Collection<PublicKey> keys)
            throws UntrustedException {
        if (keys.stream().noneMatch(key -> Keys.verify(key, manifest, signature))) {
            throw new UntrustedException("Release isn't signed by a trusted key");
        }
        return parse(manifest);
    }

    private static Release parse(byte[] manifest) throws UntrustedException {
        try {
            var release = JSON.readValue(manifest, Release.class);
            if (release == null) {
                throw new UntrustedException("Release manifest is empty");
            }
            return release;
        } catch (JacksonException e) {
            throw new UntrustedException("Release manifest isn't valid: " + e.getOriginalMessage(), e);
        }
    }

    private static long buildNumber(String version) {
        return Long.parseLong(version.substring(version.lastIndexOf('.') + 1));
    }

    @SneakyThrows(NoSuchAlgorithmException.class) // Every JDK supports SHA-256.
    private static String sha256Of(Path file) throws IOException {
        try (var in = new DigestInputStream(Files.newInputStream(file), MessageDigest.getInstance("SHA-256"))) {
            in.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(in.getMessageDigest().digest());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
