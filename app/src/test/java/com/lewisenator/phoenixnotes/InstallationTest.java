package com.lewisenator.phoenixnotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.lewisenator.phoenixnotes.signing.KeyChain;
import com.lewisenator.phoenixnotes.signing.Keys;
import com.lewisenator.phoenixnotes.signing.Release;
import com.lewisenator.phoenixnotes.signing.UntrustedException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstallationTest {

    private final KeyPair current = Keys.generate();
    private final KeyPair next = Keys.generate();
    private final KeyPair breakGlass = Keys.generate();
    private final KeyChain builtIn;
    private final DataFolder folder;
    private final Installation installation;

    InstallationTest(@TempDir Path dataDir) throws Exception {
        builtIn = KeyChain.start(breakGlass.getPrivate(), encode(current), encode(next), encode(breakGlass));
        folder = new DataFolder(dataDir);
        installation = new Installation(folder, builtIn);
    }

    @Test
    void usesTheKeysBuiltIntoTheApp() throws Exception {
        var builtInKeys = KeyChain.read(Files.readAllBytes(Path.of("../trust/keys.json")));

        assertThat(Installation.in(folder).trustedKeys()).isEqualTo(builtInKeys);
    }

    @Test
    void installsASignedReleaseWithoutMakingItCurrent() throws Exception {
        assertThat(install("1.0.5", "jar 1.0.5", current)).isEqualTo("1.0.5");
        assertThat(installation.currentVersion()).isEmpty();

        installation.makeCurrent("1.0.5");

        assertThat(installation.currentVersion()).hasValue("1.0.5");
        assertThat(installation.jar("1.0.5")).hasContent("jar 1.0.5");
        installation.verify("1.0.5");
    }

    @Test
    void refusesAReleaseNotSignedByTheCurrentKey() {
        assertThatExceptionOfType(UntrustedException.class).isThrownBy(() -> install("1.0.5", "jar", next));

        assertThat(folder.current()).doesNotExist();
    }

    @Test
    void refusesAJarThatDoesNotMatchItsManifest() throws Exception {
        var jar = Files.writeString(installation.newDownloadFile(), "tampered");
        var manifest = new Release("1.0.5", URI.create("https://example.com/a.jar"), "a".repeat(64), 8).toJson();

        assertThatExceptionOfType(UntrustedException.class)
                .isThrownBy(() -> installation.install(manifest, Keys.sign(current.getPrivate(), manifest), jar));
        assertThat(folder.current()).doesNotExist();
    }

    @Test
    void catchesAVersionChangedOnDisk() throws Exception {
        install("1.0.5", "jar 1.0.5", current);
        Files.writeString(installation.jar("1.0.5"), "tampered");

        assertThatExceptionOfType(UntrustedException.class).isThrownBy(() -> installation.verify("1.0.5"));
    }

    @Test
    void catchesAnotherVersionsFilesCopiedIntoPlace() throws Exception {
        install("1.0.5", "jar 1.0.5", current);
        Files.createDirectories(folder.version("1.0.6"));
        for (var file : List.of("app.jar", "manifest.json", "manifest.json.sig")) {
            Files.copy(
                    folder.version("1.0.5").resolve(file),
                    folder.version("1.0.6").resolve(file));
        }

        assertThatExceptionOfType(UntrustedException.class)
                .isThrownBy(() -> installation.verify("1.0.6"))
                .withMessage("Manifest is for 1.0.5, not 1.0.6");
    }

    @Test
    void keepsInstalledVersionsTrustedThroughARotation() throws Exception {
        install("1.0.5", "jar 1.0.5", current);

        installation.trustKeys(
                builtIn.rotate(next.getPrivate(), encode(Keys.generate())).toJson());

        installation.verify("1.0.5");
        assertThat(new Installation(folder, builtIn).trustedKeys().latest().number())
                .as("saved, so trusted after a restart")
                .isEqualTo(1);
    }

    @Test
    void distrustsInstalledVersionsAfterBreakGlass() throws Exception {
        install("1.0.5", "jar 1.0.5", current);

        installation.trustKeys(builtIn.breakGlass(
                        breakGlass.getPrivate(),
                        encode(Keys.generate()),
                        encode(Keys.generate()),
                        encode(Keys.generate()))
                .toJson());

        assertThatExceptionOfType(UntrustedException.class).isThrownBy(() -> installation.verify("1.0.5"));
    }

    @Test
    void ignoresASavedKeyChainItDoesNotTrust() throws Exception {
        var attacker = Keys.generate();
        var theirs = KeyChain.start(attacker.getPrivate(), encode(attacker), encode(attacker), encode(attacker))
                .rotate(attacker.getPrivate(), encode(attacker));
        Files.write(folder.keys(), theirs.toJson());

        assertThat(installation.trustedKeys()).isEqualTo(builtIn);
    }

    /** Installs a release the way a download does: signed manifest, matching jar. */
    private String install(String version, String jarContents, KeyPair signer) throws Exception {
        var jar = Files.writeString(installation.newDownloadFile(), jarContents);
        var manifest = Release.of(jar, version, URI.create("https://example.com/app.jar"))
                .toJson();
        return installation.install(manifest, Keys.sign(signer.getPrivate(), manifest), jar);
    }

    private static String encode(KeyPair keys) {
        return Keys.encode(keys.getPublic());
    }
}
