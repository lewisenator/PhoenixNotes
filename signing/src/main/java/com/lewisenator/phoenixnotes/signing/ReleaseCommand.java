package com.lewisenator.phoenixnotes.signing;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Describes a release jar, signs its manifest, and checks the signature against the current key in
 * keys.json before writing anything. CI runs this for every release.
 */
@Command(name = "release", description = "Signs a release jar's manifest and checks it against keys.json.")
final class ReleaseCommand implements Callable<Void> {

    private final Map<String, String> env;

    @Spec
    CommandSpec spec;

    @Parameters(description = "The release jar.")
    Path jar;

    @Option(names = "--version", required = true, description = "The release version, e.g. 1.0.42.")
    String version;

    @Option(names = "--url", required = true, description = "Where apps will download the jar.")
    URI url;

    @Option(names = "--keys", required = true, description = "The trusted key chain, trust/keys.json.")
    Path keysFile;

    @Option(names = "--out", defaultValue = ".", description = "Where to write the manifest and signature.")
    Path outDir;

    @Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Show this help and exit.")
    boolean help;

    ReleaseCommand(Map<String, String> env) {
        this.env = env;
    }

    @Override
    public Void call() throws IOException, UntrustedException {
        var encodedKey = env.getOrDefault(CiSecrets.SIGNING_KEY, "");
        if (encodedKey.isBlank()) {
            throw new IllegalStateException(CiSecrets.SIGNING_KEY + " is not set");
        }
        var manifest = Release.of(jar, version, url).toJson();
        var signature = Keys.sign(Keys.decodePrivate(encodedKey), manifest);
        // Refuse to publish a release apps won't trust, e.g. after a rotation that isn't pushed yet.
        var trusted = KeyChain.read(Files.readAllBytes(keysFile)).currentKey();
        Release.verify(manifest, signature, List.of(trusted));

        Files.createDirectories(outDir);
        Files.write(outDir.resolve("manifest.json"), manifest);
        Files.writeString(outDir.resolve("manifest.json.sig"), signature);
        spec.commandLine().getOut().println("Signed release " + version);
        return null;
    }
}
