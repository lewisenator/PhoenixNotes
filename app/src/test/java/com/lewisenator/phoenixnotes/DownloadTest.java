package com.lewisenator.phoenixnotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import com.lewisenator.phoenixnotes.signing.KeyChain;
import com.lewisenator.phoenixnotes.signing.Keys;
import com.lewisenator.phoenixnotes.signing.Release;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Serves a release from a folder the way GitHub does, including the "latest" redirect. */
class DownloadTest {

    @TempDir
    Path served;

    @TempDir
    Path dataDir;

    private HttpServer server;
    private URI latest;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            var path = exchange.getRequestURI().getPath();
            if (path.startsWith("/latest/")) {
                exchange.getResponseHeaders().set("Location", path.replace("/latest/", "/files/"));
                exchange.sendResponseHeaders(302, -1);
            } else if (Files.isRegularFile(served.resolve(path.substring("/files/".length())))) {
                var body = Files.readAllBytes(served.resolve(path.substring("/files/".length())));
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
            exchange.close();
        });
        server.start();
        latest = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/latest/");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
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
        var jar = Files.writeString(served.resolve("app-1.0.5.jar"), "jar 1.0.5");
        var manifest = Release.of(jar, "1.0.5", latest.resolve("app-1.0.5.jar")).toJson();
        Files.write(served.resolve("keys.json"), keys.toJson());
        Files.write(served.resolve("manifest.json"), manifest);
        Files.writeString(served.resolve("manifest.json.sig"), Keys.sign(current.getPrivate(), manifest));
        var installation = new Installation(new DataFolder(dataDir), keys);
        var download = new Download(latest);

        installation.trustKeys(download.bytes("keys.json"));
        var release = download.bytes("manifest.json");
        var signature = new String(download.bytes("manifest.json.sig"), StandardCharsets.UTF_8);
        var downloadedJar = installation.newDownloadFile();
        download.toFile(latest.resolve("app-1.0.5.jar"), downloadedJar);

        assertThat(installation.install(release, signature, downloadedJar)).isEqualTo("1.0.5");
        assertThat(installation.jar("1.0.5")).hasContent("jar 1.0.5");
    }

    @Test
    void failsForAMissingFile() {
        assertThatIOException()
                .isThrownBy(() -> new Download(latest).bytes("missing.json"))
                .withMessageContaining("404");
    }
}
