package com.lewisenator.phoenixnotes.signing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseCommandTest {

    @TempDir
    Path folder;

    private final KeyPair current = Keys.generate();
    private final StringWriter err = new StringWriter();
    private Path jar;
    private Path keysFile;

    @BeforeEach
    void setUp() throws Exception {
        jar = Files.writeString(folder.resolve("app.jar"), "jar contents");
        var breakGlass = Keys.generate();
        var chain = KeyChain.start(
                breakGlass.getPrivate(),
                Keys.encode(current.getPublic()),
                Keys.encode(Keys.generate().getPublic()),
                Keys.encode(breakGlass.getPublic()));
        keysFile = Files.write(folder.resolve("keys.json"), chain.toJson());
    }

    @Test
    void signsAReleaseAppsWillTrust() throws Exception {
        assertThat(run(current)).isZero();

        var manifest = Files.readAllBytes(folder.resolve("out/manifest.json"));
        var signature = Files.readString(folder.resolve("out/manifest.json.sig"));
        var release = Release.verify(manifest, signature, List.of(current.getPublic()));
        assertThat(release.version()).isEqualTo("1.0.42");
        release.checkJar(jar);
    }

    @Test
    void refusesAKeyThatIsNotTheCurrentKey() {
        assertThat(run(Keys.generate())).isEqualTo(1);

        assertThat(err.toString()).contains("isn't signed by a trusted key");
        assertThat(folder.resolve("out")).doesNotExist();
    }

    @Test
    void needsTheSigningKey() {
        var exitCode =
                SigningTool.commandLine(Map.of()).setErr(new PrintWriter(err)).execute(args());

        assertThat(exitCode).isEqualTo(1);
        assertThat(err.toString()).contains(ReleaseCommand.SIGNING_KEY + " is not set");
    }

    private int run(KeyPair signer) {
        var env = Map.of(ReleaseCommand.SIGNING_KEY, Keys.encode(signer.getPrivate()));
        return SigningTool.commandLine(env).setErr(new PrintWriter(err)).execute(args());
    }

    private String[] args() {
        return new String[] {
            "release",
            jar.toString(),
            "--version",
            "1.0.42",
            "--url",
            "https://example.com/app-1.0.42.jar",
            "--keys",
            keysFile.toString(),
            "--out",
            folder.resolve("out").toString()
        };
    }
}
