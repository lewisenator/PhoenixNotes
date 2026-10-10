package com.lewisenator.phoenixnotes.signing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseTest {

    private static final URI URL = URI.create("https://example.com/app-1.0.7.jar");

    @TempDir
    Path folder;

    private final KeyPair keys = Keys.generate();
    private final Release release = new Release("1.0.7", URL, "a".repeat(64), 5);

    @Test
    void describesAJar() throws Exception {
        var jar = Files.writeString(folder.resolve("app.jar"), "hello");

        var described = Release.of(jar, "1.0.7", URL);

        assertThat(described.size()).isEqualTo(5);
        // sha256("hello")
        assertThat(described.sha256()).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        described.checkJar(jar);
    }

    @Test
    void rejectsAJarThatDoesNotMatch() throws Exception {
        var jar = Files.writeString(folder.resolve("app.jar"), "hello");
        var described = Release.of(jar, "1.0.7", URL);
        Files.writeString(jar, "jello");

        assertThatExceptionOfType(UntrustedException.class).isThrownBy(() -> described.checkJar(jar));
    }

    @Test
    void verifiesASignedManifest() throws Exception {
        var json = release.toJson();

        assertThat(Release.verify(json, Keys.sign(keys.getPrivate(), json), List.of(keys.getPublic())))
                .isEqualTo(release);
    }

    @Test
    void acceptsASignatureFromAnyTrustedKey() throws Exception {
        var json = release.toJson();
        var trusted = List.of(Keys.generate().getPublic(), keys.getPublic());

        assertThat(Release.verify(json, Keys.sign(keys.getPrivate(), json), trusted))
                .isEqualTo(release);
    }

    @Test
    void rejectsAManifestChangedAfterSigning() {
        var json = release.toJson();
        var signature = Keys.sign(keys.getPrivate(), json);
        json[json.length / 2] ^= 1;

        assertUntrusted(json, signature);
    }

    @Test
    void rejectsAnUntrustedKey() {
        var json = release.toJson();

        assertUntrusted(json, Keys.sign(Keys.generate().getPrivate(), json));
    }

    @Test
    void rejectsAMalformedSignature() {
        assertUntrusted(release.toJson(), "not base64!");
    }

    @Test
    void rejectsSignedButInvalidContent() {
        for (var content : List.of("{\"version\": \"1.0.7\"}", "null", "not json")) {
            var bytes = content.getBytes(StandardCharsets.UTF_8);
            assertUntrusted(bytes, Keys.sign(keys.getPrivate(), bytes));
        }
    }

    @Test
    void ignoresFieldsAddedByNewerReleases() throws Exception {
        var json = """
                {"version": "1.0.7", "url": "%s", "sha256": "%s", "size": 5, "addedLater": true}
                """.formatted(URL, "a".repeat(64)).getBytes(StandardCharsets.UTF_8);

        assertThat(Release.verify(json, Keys.sign(keys.getPrivate(), json), List.of(keys.getPublic())))
                .isEqualTo(release);
    }

    @Test
    void comparesVersionsByTheirLastNumber() {
        var v12 = new Release("1.0.12", URL, "a".repeat(64), 5);

        // 12 > 5, even though "1.0.12" sorts before "1.0.5" as text.
        assertThat(v12.isNewerThan("1.0.5")).isTrue();
        assertThat(v12.isNewerThan("1.0.12")).isFalse();
        assertThat(v12.isNewerThan("1.0.13")).isFalse();
    }

    @Test
    void rejectsInvalidFields() {
        var sha = "a".repeat(64);
        assertThatIllegalArgumentException().isThrownBy(() -> new Release("../evil", URL, sha, 5));
        assertThatIllegalArgumentException().isThrownBy(() -> new Release("1.0.7", URI.create("app.jar"), sha, 5));
        assertThatIllegalArgumentException().isThrownBy(() -> new Release("1.0.7", URL, "A".repeat(64), 5));
        assertThatIllegalArgumentException().isThrownBy(() -> new Release("1.0.7", URL, sha, 0));
    }

    private void assertUntrusted(byte[] manifest, String signature) {
        assertThatExceptionOfType(UntrustedException.class)
                .isThrownBy(() -> Release.verify(manifest, signature, List.of(keys.getPublic())));
    }
}
