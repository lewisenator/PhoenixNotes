package com.lewisenator.phoenixnotes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class MainTest {

    private final StringWriter out = new StringWriter();

    @Test
    void printsHelp() {
        var command = new CommandLine(new Main()).setOut(new PrintWriter(out));

        assertThat(command.execute("--help")).isZero();
        assertThat(out.toString()).contains("--data-dir");
    }

    @Test
    void rejectsUnknownOptions() {
        var command = new CommandLine(new Main()).setErr(new PrintWriter(out));

        assertThat(command.execute("--nope")).isEqualTo(2);
        assertThat(out.toString()).contains("Unknown option");
    }
}
