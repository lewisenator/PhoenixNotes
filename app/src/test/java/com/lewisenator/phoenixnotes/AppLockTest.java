package com.lewisenator.phoenixnotes;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppLockTest {

    @TempDir
    Path folder;

    @Test
    void onlyOneHolderAtATime() throws Exception {
        var first = new AppLock(folder.resolve("app.lock"));
        var second = new AppLock(folder.resolve("app.lock"));

        assertThat(first.take(Duration.ZERO)).isTrue();
        assertThat(first.take(Duration.ZERO)).as("already held").isTrue();
        assertThat(second.take(Duration.ofMillis(100))).isFalse();

        first.release();

        assertThat(second.take(Duration.ZERO)).isTrue();
        second.release();
    }
}
