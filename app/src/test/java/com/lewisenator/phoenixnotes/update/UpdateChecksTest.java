package com.lewisenator.phoenixnotes.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.lewisenator.phoenixnotes.AppLock;
import com.lewisenator.phoenixnotes.DataFolder;
import com.lewisenator.phoenixnotes.signing.KeyChain;
import com.lewisenator.phoenixnotes.signing.Keys;
import com.lewisenator.phoenixnotes.ui.Notepad;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Opens a real window, so it needs a display (CI uses a virtual one on Linux). */
class UpdateChecksTest {

    @TempDir
    Path served;

    @TempDir
    Path dataDir;

    private final KeyPair current = Keys.generate();
    private KeyChain keys;
    private ReleaseServer server;
    private Notepad notepad;
    private UpdateChecks checks;

    @BeforeEach
    void setUp() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        var breakGlass = Keys.generate();
        keys = KeyChain.start(
                breakGlass.getPrivate(),
                Keys.encode(current.getPublic()),
                Keys.encode(Keys.generate().getPublic()),
                Keys.encode(breakGlass.getPublic()));
        server = new ReleaseServer(served);
        var folder = new DataFolder(dataDir);
        notepad = Notepad.open(folder, "1.0.6", Optional.empty());
        checks = new UpdateChecks(
                new Installation(folder, keys),
                new Download(server.latest()),
                "1.0.6",
                notepad,
                new AppLock(folder.lock()),
                () -> {});
    }

    @AfterEach
    void tearDown() throws Exception {
        if (notepad != null) {
            SwingUtilities.invokeAndWait(() -> notepad.frame().dispose());
            server.close();
        }
    }

    @Test
    void showsWhenItIsUpToDate() throws Exception {
        publish("1.0.6");

        checks.check();

        assertThat(button()).isEqualTo("Up to date");
    }

    @Test
    void showsWhenItCannotCheck() throws Exception {
        checks.check(); // Nothing published: 404.

        assertThat(button()).isEqualTo("Couldn't check for updates");
    }

    @Test
    void checksAsSoonAsItStarts() throws Exception {
        publish("1.0.6");

        checks.start();

        var giveUpAt = Instant.now().plus(Duration.ofSeconds(10));
        while (!button().equals("Up to date") && Instant.now().isBefore(giveUpAt)) {
            Thread.sleep(50);
        }
        assertThat(button()).isEqualTo("Up to date");
    }

    private void publish(String version) throws Exception {
        var jar = Files.writeString(dataDir.resolve("built.jar"), "jar " + version);
        server.publish(jar, version, keys, current.getPrivate());
    }

    /** The button's text, once the event thread has caught up. */
    private String button() throws Exception {
        var text = new AtomicReference<String>();
        SwingUtilities.invokeAndWait(() -> text.set(notepad.updateStatus().getText()));
        return text.get();
    }
}
