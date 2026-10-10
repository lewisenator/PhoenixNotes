package com.lewisenator.phoenixnotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import com.lewisenator.phoenixnotes.signing.KeyChain;
import com.lewisenator.phoenixnotes.signing.Keys;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DownloadTest {

    @TempDir
    Path served;

    @TempDir
    Path dataDir;

    private ReleaseServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new ReleaseServer(served);
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void downloadsAReleaseThatInstallationThenInstalls() throws Exception {
        var current = Keys.generate();
        var breakGlass = Keys.generate();
        var keys = KeyChain.start(
                breakGlass.getPrivate(),
                Keys.encode(current.getPublic()),
                Keys.encode(Keys.generate().getPublic()),
                Keys.encode(breakGlass.getPublic()));
        server.publish(
                Files.writeString(dataDir.resolve("built.jar"), "jar 1.0.5"), "1.0.5", keys, current.getPrivate());
        var installation = new Installation(new DataFolder(dataDir), keys);
        var download = new Download(server.latest());

        installation.trustKeys(download.bytes("keys.json"));
        var release = download.bytes("manifest.json");
        var signature = new String(download.bytes("manifest.json.sig"), StandardCharsets.UTF_8);
        var downloadedJar = installation.newDownloadFile();
        download.toFile(server.latest().resolve("app-1.0.5.jar"), downloadedJar);

        assertThat(installation.install(release, signature, downloadedJar)).isEqualTo("1.0.5");
        assertThat(installation.jar("1.0.5")).hasContent("jar 1.0.5");
    }

    @Test
    void failsForAMissingFile() {
        assertThatIOException()
                .isThrownBy(() -> new Download(server.latest()).bytes("missing.json"))
                .withMessageContaining("404");
    }
}
