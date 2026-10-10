package com.lewisenator.phoenixnotes.signing;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Sets up, rotates and replaces the signing keys (see docs/decisions/0009-key-rotation.md). Each
 * command checks everything before changing anything, then changes things in an order a failure can
 * be recovered from: new keys are saved before anything depends on them.
 */
@Command(name = "keys", description = "Sets up, rotates and replaces the release signing keys.")
final class KeysCommand {

    static final String NEXT = "PhoenixNotes next signing key";
    static final String BREAK_GLASS = "PhoenixNotes break-glass key";
    static final String PENDING = " (pending)";

    private final Vault vault;
    private final CiSecrets ci;
    private final Shell shell;
    private final BufferedReader input;

    @Spec
    CommandSpec spec;

    @Option(
            names = "--keys",
            defaultValue = "trust/keys.json",
            description = "The key chain (default: ${DEFAULT-VALUE}).")
    Path keysFile;

    KeysCommand(Vault vault, CiSecrets ci, Shell shell, BufferedReader input) {
        this.vault = vault;
        this.ci = ci;
        this.shell = shell;
        this.input = input;
    }

    @Command(name = "init", description = "One-time setup: creates all three keys. Refuses if any exist.")
    void init() throws IOException, UntrustedException {
        // 1. Check everything first. Never replace existing keys.
        if (Files.exists(keysFile)) {
            throw refused(keysFile + " already exists. To rotate keys, run keysRotate.");
        }
        for (var item : List.of(NEXT, BREAK_GLASS, NEXT + PENDING, BREAK_GLASS + PENDING)) {
            if (vault.has(item)) {
                throw refused("1Password already has \"" + item + "\". Remove it if you mean to start over.");
            }
        }
        if (ci.hasSigningKey()) {
            throw refused("CI already has a signing key. It can't be read back; delete it to start over.");
        }

        // 2. Create the keys and sign generation 0 with the break-glass key, in memory.
        var current = Keys.generate();
        var next = Keys.generate();
        var breakGlass = Keys.generate();
        var chain = KeyChain.start(breakGlass.getPrivate(), encode(current), encode(next), encode(breakGlass));

        // 3. Store the private keys: the offline ones in 1Password, the current one in CI.
        vault.save(NEXT, next);
        vault.save(BREAK_GLASS, breakGlass);
        ci.setSigningKey(current.getPrivate());

        // 4. Write keys.json last, so a failure above leaves no half-finished setup behind.
        Files.createDirectories(keysFile.toAbsolutePath().getParent());
        Files.write(keysFile, chain.toJson());
        say("Created " + keysFile + ". Commit and push it.");
    }

    @Command(name = "rotate", description = "Routine rotation: next becomes current (in CI), and a new next is made.")
    void rotate() throws IOException, UntrustedException {
        // 1. Start from the committed, pushed keys.json.
        var chain = readCommittedKeys();

        // 2. Sign the next generation with the old next key. Fails if it isn't the key keys.json trusts.
        var oldNext = Keys.decodePrivate(vault.read(NEXT));
        var newNext = Keys.generate();
        var rotated = chain.rotate(oldNext, encode(newNext));
        Files.write(keysFile, rotated.toJson());

        // 3. Save the new next key before anything depends on it; then move the promoted key to CI.
        vault.save(NEXT + PENDING, newNext);
        ci.setSigningKey(oldNext);
        vault.replaceWithPending(NEXT);
        say("Rotated to generation " + rotated.latest().number() + ". Commit and push " + keysFile
                + " now: releases fail until it's on main.");
    }

    @Command(name = "break-glass", description = "Emergency: replaces every key and distrusts older versions.")
    void breakGlass() throws IOException, UntrustedException {
        // 1. Start from the committed, pushed keys.json, and make sure this is really wanted.
        var chain = readCommittedKeys();
        say("Break glass replaces all three keys and distrusts every installed version signed before.");
        say("Type 'break glass' to continue:");
        if (!"break glass".equals(input.readLine())) {
            throw refused("Cancelled; nothing was changed.");
        }

        // 2. Sign a generation with all-new keys, using the old break-glass key.
        var oldBreakGlass = Keys.decodePrivate(vault.read(BREAK_GLASS));
        var current = Keys.generate();
        var next = Keys.generate();
        var breakGlass = Keys.generate();
        var replaced = chain.breakGlass(oldBreakGlass, encode(current), encode(next), encode(breakGlass));
        Files.write(keysFile, replaced.toJson());

        // 3. Save the new offline keys before anything depends on them; then switch CI and 1Password.
        vault.save(NEXT + PENDING, next);
        vault.save(BREAK_GLASS + PENDING, breakGlass);
        ci.setSigningKey(current.getPrivate());
        vault.replaceWithPending(NEXT);
        vault.replaceWithPending(BREAK_GLASS);
        say("Replaced every key at generation " + replaced.latest().number() + ". Commit and push " + keysFile
                + " now: releases fail until it's on main.");
    }

    /**
     * Reads keys.json, refusing unless it's committed and matches origin/main, so a rotation never
     * starts from a stale or half-finished copy. Also refuses if an earlier run left pending items.
     */
    private KeyChain readCommittedKeys() throws IOException, UntrustedException {
        if (!Files.exists(keysFile)) {
            throw refused("No " + keysFile + ". Run keysInit first.");
        }
        if (!shell.run(List.of("git", "status", "--porcelain", "--", keysFile.toString()), "")
                .isBlank()) {
            throw refused(keysFile + " isn't committed. If an earlier run failed partway, see the README.");
        }
        shell.run(List.of("git", "fetch", "--quiet", "origin", "main"), "");
        if (!shell.run(List.of("git", "diff", "--name-only", "origin/main", "--", keysFile.toString()), "")
                .isBlank()) {
            throw refused(keysFile + " differs from origin/main. Pull or push first.");
        }
        for (var item : List.of(NEXT + PENDING, BREAK_GLASS + PENDING)) {
            if (vault.has(item)) {
                throw refused("1Password has \"" + item + "\" from a run that didn't finish. See the README.");
            }
        }
        return KeyChain.read(Files.readAllBytes(keysFile));
    }

    private void say(String message) {
        spec.commandLine().getOut().println(message);
    }

    private static String encode(KeyPair keys) {
        return Keys.encode(keys.getPublic());
    }

    private static IllegalStateException refused(String message) {
        return new IllegalStateException(message);
    }
}
