package com.lewisenator.phoenixnotes;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartupTest {

    @TempDir
    Path folder;

    @Test
    void theFirstCopyGetsTheDataFolder() throws Exception {
        var startup = Startup.in(new DataFolder(folder)).lockDataFolder();

        assertThat(startup.alreadyRunning()).isFalse();
    }

    @Test
    void aSecondCopyDoesNothing() throws Exception {
        var dataFolder = new DataFolder(folder);
        Startup.in(dataFolder).lockDataFolder();

        var second = Startup.in(dataFolder).lockDataFolder();

        assertThat(second.alreadyRunning()).isTrue();
        assertThat(second.openNotepad()).isEmpty();
    }
}
