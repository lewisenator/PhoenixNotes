package com.lewisenator.phoenixnotes.signing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs real processes; java is the one command every machine running these tests has. */
class ShellTest {

    private final String java =
            Path.of(System.getProperty("java.home"), "bin", "java").toString();

    @Test
    void returnsWhatTheCommandPrinted() throws Exception {
        assertThat(Shell.system().run(List.of(java, "-version"), "")).contains("version");
    }

    @Test
    void failsWhenTheCommandFails() {
        assertThatIOException()
                .isThrownBy(() -> Shell.system().run(List.of(java, "--no-such-option"), ""))
                .withMessageContaining("failed");
    }
}
