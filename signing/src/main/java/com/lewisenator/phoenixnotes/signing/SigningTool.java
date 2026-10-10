package com.lewisenator.phoenixnotes.signing;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command-line tool for signing releases and managing the signing keys. Private keys only ever come
 * from environment variables, 1Password or CI secrets, never from arguments.
 */
@Command(name = "signing", description = "Signs Phoenix Notes releases and manages the signing keys.")
public final class SigningTool {

    @Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Show this help and exit.")
    boolean help;

    public static void main(String[] args) {
        var env = System.getenv();
        var shell = Shell.system();
        var vault = Vault.onePassword(shell, env.getOrDefault("PHOENIXNOTES_OP_VAULT", "Private"));
        var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.exit(
                commandLine(env, vault, CiSecrets.gitHub(shell), shell, input).execute(args));
    }

    static CommandLine commandLine(
            Map<String, String> env, Vault vault, CiSecrets ci, Shell shell, BufferedReader input) {
        return new CommandLine(new SigningTool())
                .addSubcommand(new ReleaseCommand(env))
                .addSubcommand(new KeysCommand(vault, ci, shell, input))
                // Any command can throw; the user sees one line, not a stack trace.
                .setExecutionExceptionHandler((e, commandLine, parseResult) -> {
                    commandLine.getErr().println("Error: " + e.getMessage());
                    return 1;
                });
    }
}
