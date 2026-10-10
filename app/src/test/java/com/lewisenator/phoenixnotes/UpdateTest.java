package com.lewisenator.phoenixnotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.lewisenator.phoenixnotes.signing.KeyChain;
import com.lewisenator.phoenixnotes.signing.Keys;
import com.lewisenator.phoenixnotes.signing.UntrustedException;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Updates from a local server that works like GitHub's. The handoff tests start this app's real jar
 * as the new version, like {@link HandoffTest}, so they need a display.
 */
class UpdateTest {

    private static final Path APP_JAR = Path.of(System.getProperty("phoenixnotes.appJar"));

    private final KeyPair current = Keys.generate();
    private final KeyPair next = Keys.generate();
    private final KeyPair breakGlass = Keys.generate();
    private final KeyChain keys;
    private final Path built;
    private final DataFolder folder;
    private final Installation installation;
    private final ReleaseServer server;
    private final AppLock lock;
    private final AtomicBoolean exited = new AtomicBoolean();
    private Notepad notepad;

    UpdateTest(@TempDir Path served, @TempDir Path built, @TempDir Path dataDir) throws Exception {
        keys = KeyChain.start(breakGlass.getPrivate(), encode(current), encode(next), encode(breakGlass));
        this.built = built;
        folder = new DataFolder(dataDir);
        installation = new Installation(folder, keys);
        server = new ReleaseServer(served);
        lock = new AppLock(folder.lock());
        lock.take(Duration.ZERO);
    }

    @AfterEach
    void cleanUp() throws Exception {
        ProcessHandle.current().descendants().toList().forEach(process -> {
            process.destroyForcibly();
            process.onExit().join();
        });
        if (notepad != null) {
            SwingUtilities.invokeAndWait(() -> notepad.frame().dispose());
        }
        lock.release();
        server.close();
    }

    @Test
    void handsOverToANewerRelease() throws Exception {
        openNotepad("typed since the last save");
        server.publish(APP_JAR, "1.0.6", keys, current.getPrivate());

        update("1.0.5").handOff(notepad, lock, () -> exited.set(true));

        assertThat(exited).as("exited").isTrue();
        assertThat(installation.currentVersion()).hasValue("1.0.6");
        assertThat(new Note(folder.note()).read()).isEqualTo("typed since the last save");
        assertThat(new AppLock(folder.lock()).take(Duration.ZERO))
                .as("the new version holds the data folder")
                .isFalse();
    }

    @Test
    void keepsRunningIfTheNewVersionFailsToTakeOver() throws Exception {
        openNotepad("hello");
        server.publish(jar("not a jar"), "1.0.6", keys, current.getPrivate());

        update("1.0.5").handOff(notepad, lock, () -> exited.set(true));

        assertThat(exited).as("exited").isFalse();
        assertThat(installation.currentVersion()).isEmpty();
        assertThat(installation.hasFailed("1.0.6")).isTrue();
        SwingUtilities.invokeAndWait(() ->
                assertThat(notepad.overlay().message().getText()).isEqualTo("Update to 1.0.6 failed — still on 1.0.5"));
        assertThat(new AppLock(folder.lock()).take(Duration.ZERO))
                .as("still holds the data folder")
                .isFalse();
    }

    @Test
    void installsAReleaseSignedByANewlyRotatedKey() throws Exception {
        var rotated = keys.rotate(next.getPrivate(), encode(Keys.generate()));
        server.publish(jar("jar 1.0.6"), "1.0.6", rotated, next.getPrivate());

        update("1.0.5");

        installation.verify("1.0.6");
    }

    @Test
    void refusesAReleaseNotSignedByTheCurrentKey() throws Exception {
        server.publish(jar("jar 1.0.6"), "1.0.6", keys, next.getPrivate());

        assertThatExceptionOfType(UntrustedException.class).isThrownBy(() -> update("1.0.5"));
        assertThat(folder.version("1.0.6")).doesNotExist();
    }

    @Test
    void skipsAReleaseThatIsNotNewer() throws Exception {
        server.publish(jar("jar 1.0.6"), "1.0.6", keys, current.getPrivate());

        update("1.0.6");

        assertThat(folder.version("1.0.6")).doesNotExist();
    }

    @Test
    void skipsAVersionThatFailedBefore() throws Exception {
        server.publish(jar("jar 1.0.6"), "1.0.6", keys, current.getPrivate());
        Files.createDirectories(folder.version("1.0.6"));
        installation.markFailed("1.0.6");

        update("1.0.5");

        assertThat(installation.jar("1.0.6")).doesNotExist();
    }

    @Test
    void devBuildsDoNotUpdate() throws Exception {
        server.publish(jar("jar 1.0.6"), "1.0.6", keys, current.getPrivate());

        update(Startup.DEV);

        assertThat(folder.version("1.0.6")).doesNotExist();
    }

    /** Every step up to the handoff. */
    private Update update(String runningVersion) throws Exception {
        return Update.check(installation, new Download(server.latest()), runningVersion)
                .fetchLatestKeys()
                .fetchLatestRelease()
                .skipUnlessNewer()
                .downloadAndInstall();
    }

    private void openNotepad(String typed) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        notepad = Notepad.open(new Note(folder.note()), "1.0.5", Optional.empty());
        SwingUtilities.invokeAndWait(() -> notepad.text().setText(typed));
    }

    private Path jar(String contents) throws Exception {
        return Files.writeString(built.resolve("app.jar"), contents);
    }

    private static String encode(KeyPair keys) {
        return Keys.encode(keys.getPublic());
    }
}
