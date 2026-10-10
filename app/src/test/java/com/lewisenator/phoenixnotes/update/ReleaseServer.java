package com.lewisenator.phoenixnotes.update;

import com.lewisenator.phoenixnotes.signing.KeyChain;
import com.lewisenator.phoenixnotes.signing.Keys;
import com.lewisenator.phoenixnotes.signing.Release;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;

/** Serves releases from a folder the way GitHub does, including the "latest" redirect. */
final class ReleaseServer implements AutoCloseable {

    private final Path files;
    private final HttpServer server;

    ReleaseServer(Path files) throws IOException {
        this.files = files;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            var path = exchange.getRequestURI().getPath();
            var file = files.resolve(path.substring(path.lastIndexOf('/') + 1));
            if (path.startsWith("/latest/")) {
                exchange.getResponseHeaders().set("Location", path.replace("/latest/", "/files/"));
                exchange.sendResponseHeaders(302, -1);
            } else if (Files.isRegularFile(file)) {
                var body = Files.readAllBytes(file);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
            exchange.close();
        });
        server.start();
    }

    /** Like {@link Download#LATEST_RELEASE}. */
    URI latest() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/latest/");
    }

    /** Publishes {@code jar} as the latest release, signed by {@code signer}, along with {@code keys}. */
    void publish(Path jar, String version, KeyChain keys, PrivateKey signer) throws IOException {
        var name = "app-" + version + ".jar";
        Files.copy(jar, files.resolve(name));
        var manifest = Release.of(jar, version, latest().resolve(name)).toJson();
        Files.write(files.resolve("keys.json"), keys.toJson());
        Files.write(files.resolve("manifest.json"), manifest);
        Files.writeString(files.resolve("manifest.json.sig"), Keys.sign(signer, manifest));
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
