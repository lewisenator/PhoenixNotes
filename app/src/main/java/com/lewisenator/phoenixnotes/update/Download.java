package com.lewisenator.phoenixnotes.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Fetches files from the latest release. It only brings bytes; {@link Installation} decides whether
 * to trust them, so the host never has to be trusted (see docs/decisions/0007-public-repository.md).
 */
public final class Download {

    public static final URI LATEST_RELEASE =
            URI.create("https://github.com/lewisenator/PhoenixNotes/releases/latest/download/");

    private final URI release;
    private final HttpClient client = HttpClient.newBuilder()
            // GitHub's "latest release" URLs redirect to the actual files.
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** @param release where the release's files are, e.g. {@link #LATEST_RELEASE} */
    public Download(URI release) {
        this.release = release;
    }

    /** A small file from the release: keys.json, manifest.json or manifest.json.sig. */
    byte[] bytes(String name) throws IOException, InterruptedException {
        return get(release.resolve(name), HttpResponse.BodyHandlers.ofByteArray());
    }

    /** Streams a file, like the release's jar, into {@code into}. */
    void toFile(URI url, Path into) throws IOException, InterruptedException {
        get(url, HttpResponse.BodyHandlers.ofFile(into));
    }

    private <T> T get(URI url, HttpResponse.BodyHandler<T> body) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(url).timeout(Duration.ofMinutes(1)).build();
        var response = client.send(request, body);
        if (response.statusCode() != 200) {
            throw new IOException("GET " + url + " returned " + response.statusCode());
        }
        return response.body();
    }
}
