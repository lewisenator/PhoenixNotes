package com.lewisenator.phoenixnotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.lewisenator.phoenixnotes.signing.KeyChain;
import com.lewisenator.phoenixnotes.signing.Keys;
import com.lewisenator.phoenixnotes.signing.Release;
import com.lewisenator.phoenixnotes.update.Installation;
import java.awt.GraphicsEnvironment;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyPair;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartupTest {

    private static final Path APP_JAR = Path.of(System.getProperty("phoenixnotes.appJar"));

    private final Path built;
    private final DataFolder folder;
    private final Installation installation;
    private final KeyPair current = Keys.generate();

    StartupTest(@TempDir Path dataDir, @TempDir Path built) throws Exception {
        this.built = built;
        var breakGlass = Keys.generate();
        var keys = KeyChain.start(
                breakGlass.getPrivate(),
                Keys.encode(current.getPublic()),
                Keys.encode(Keys.generate().getPublic()),
                Keys.encode(breakGlass.getPublic()));
        folder = new DataFolder(dataDir);
        installation = new Installation(folder, keys);
    }

    @AfterEach
    void stopNewVersion() {
        ProcessHandle.current().descendants().toList().forEach(process -> {
            process.destroyForcibly();
            process.onExit().join();
        });
    }

    @Test
    void theFirstCopyGetsTheDataFolder() throws Exception {
        var startup = Startup.in(folder, false).lockDataFolder();

        assertThat(startup.alreadyRunning()).isFalse();
    }

    @Test
    void aSecondCopyDoesNothing() throws Exception {
        Startup.in(folder, false).lockDataFolder();

        var second = Startup.in(folder, false).lockDataFolder().handOffToCurrentVersion();

        assertThat(second.alreadyRunning()).isTrue();
        assertThat(second.openNotepad().notepad()).isEmpty();
    }

    @Test
    void anOlderInstallHandsOffToTheCurrentVersion() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        installCurrent("1.0.6", APP_JAR);

        var startup = start("1.0.5");

        assertThat(startup.handedOff()).isTrue();
        assertThat(startup.openNotepad().notepad()).isEmpty();
        assertThat(new AppLock(folder.lock()).take(Duration.ZERO))
                .as("the current version holds the data folder")
                .isFalse();
    }

    @Test
    void runsItselfWhenItIsTheNewest() throws Exception {
        installCurrent("1.0.6", jar("jar 1.0.6"));

        assertThat(start("1.0.7").handedOff()).isFalse();
    }

    @Test
    void runsItselfWhenTheCurrentVersionDoesNotVerify() throws Exception {
        installCurrent("1.0.6", jar("jar 1.0.6"));
        Files.writeString(installation.jar("1.0.6"), "tampered");

        assertThat(start("1.0.5").handedOff()).isFalse();
    }

    @Test
    void runsItselfWhenTheCurrentVersionFailsToTakeOver() throws Exception {
        installCurrent("1.0.6", jar("not a jar"));

        var startup = start("1.0.5");

        assertThat(startup.handedOff()).isFalse();
        assertThat(installation.hasFailed("1.0.6")).isTrue();
        assertThat(new AppLock(folder.lock()).take(Duration.ZERO))
                .as("still holds the data folder")
                .isFalse();
    }

    @Test
    void devBuildsRunThemselves() throws Exception {
        installCurrent("1.0.6", jar("jar 1.0.6"));

        assertThat(start(Startup.DEV).handedOff()).isFalse();
    }

    private Startup start(String version) throws Exception {
        return Startup.in(installation, false, version).lockDataFolder().handOffToCurrentVersion();
    }

    /** Installs a signed release of {@code jar} and makes it current, as an earlier update would. */
    private void installCurrent(String version, Path jar) throws Exception {
        var downloaded = Files.copy(jar, installation.newDownloadFile(), StandardCopyOption.REPLACE_EXISTING);
        var manifest = Release.of(downloaded, version, URI.create("https://example.com/app.jar"))
                .toJson();
        installation.install(manifest, Keys.sign(current.getPrivate(), manifest), downloaded);
        installation.makeCurrent(version);
    }

    private Path jar(String contents) throws Exception {
        return Files.writeString(built.resolve("app.jar"), contents);
    }
}
