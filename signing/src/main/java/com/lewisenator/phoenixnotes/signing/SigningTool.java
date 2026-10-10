package com.lewisenator.phoenixnotes.signing;

import java.util.Map;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Command-line tool for signing releases. Private keys only ever come from environment variables. */
@Command(name = "signing", description = "Signs Phoenix Notes releases.")
public final class SigningTool {

    @Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Show this help and exit.")
    boolean help;

    public static void main(String[] args) {
        System.exit(commandLine(System.getenv()).execute(args));
    }

    static CommandLine commandLine(Map<String, String> env) {
        return new CommandLine(new SigningTool()).addSubcommand(new ReleaseCommand(env));
    }
}
