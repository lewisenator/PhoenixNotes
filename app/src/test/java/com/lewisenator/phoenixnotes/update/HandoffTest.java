package com.lewisenator.phoenixnotes.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.lewisenator.phoenixnotes.DataFolder;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Hands over to this app's real jar, started as a separate process, the way an update does. The new
 * version opens a real window, so this needs a display (CI uses a virtual one on Linux).
 */
class HandoffTest {

    private static final Path APP_JAR = Path.of(System.getProperty("phoenixnotes.appJar"));
    private static final Rectangle WINDOW = new Rectangle(120, 80, 500, 400);

    @TempDir
    Path dataDir;

    private DataFolder folder;
    private Handoff handoff;

    @BeforeEach
    void setUp() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        folder = new DataFolder(dataDir);
    }

    @AfterEach
    void stopNewVersion() {
        if (handoff != null) {
            handoff.abandon();
        }
    }

    @Test
    void theNewVersionTakesOver() throws Exception {
        handoff = Handoff.start(APP_JAR, folder);

        handoff.awaitReady();
        handoff.go(Optional.of(WINDOW));
        handoff.awaitRunning();

        assertThat(canLock(folder)).as("the new version holds the data folder").isFalse();
    }

    @Test
    void failsIfTheNewVersionCannotStart() throws Exception {
        var broken = Files.writeString(dataDir.resolve("broken.jar"), "not a jar");
        handoff = Handoff.start(broken, folder);

        assertThatIOException().isThrownBy(handoff::awaitReady).withMessageContaining("didn't say ready");
    }

    @Test
    void failsIfTheNewVersionCannotTakeTheDataFolder() throws Exception {
        try (var channel = FileChannel.open(folder.lock(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            // As if the old version never let go of the data folder; released when the channel closes.
            assertThat(channel.lock()).isNotNull();
            handoff = Handoff.start(APP_JAR, folder);
            handoff.awaitReady();
            handoff.go(Optional.of(WINDOW));

            assertThatIOException().isThrownBy(handoff::awaitRunning).withMessageContaining("didn't say running");
        }
    }

    private static boolean canLock(DataFolder folder) throws Exception {
        try (var channel = FileChannel.open(folder.lock(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                var lock = channel.tryLock()) {
            return lock != null;
        }
    }
}
