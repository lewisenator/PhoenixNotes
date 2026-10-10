package com.lewisenator.phoenixnotes.signing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The key lifecycle, with 1Password, GitHub and git replaced by in-memory fakes. */
class KeysCommandTest {

    @TempDir
    Path folder;

    private final FakeVault vault = new FakeVault();
    private final FakeCi ci = new FakeCi();
    private final StringWriter err = new StringWriter();
    private String gitStatus = "";
    private String input = "";

    @Test
    void initCreatesAllThreeKeys() throws Exception {
        assertThat(run("init")).isZero();

        var chain = keys();
        assertThat(chain.latest().number()).isZero();
        assertThat(ciKeyMatches(chain.latest().current())).isTrue();
        assertThat(vault.publicKey(KeysCommand.NEXT)).isEqualTo(chain.latest().next());
        assertThat(vault.publicKey(KeysCommand.BREAK_GLASS))
                .isEqualTo(chain.latest().breakGlass());
    }

    @Test
    void initNeverReplacesExistingKeys() throws Exception {
        ci.key = Optional.of(Keys.generate().getPrivate());

        assertThat(run("init")).isEqualTo(1);

        assertThat(err.toString()).contains("CI already has a signing key");
        assertThat(keysFile()).doesNotExist();
        assertThat(vault.items).isEmpty();
    }

    @Test
    void rotationMovesNextIntoCiAndMakesANewNext() throws Exception {
        run("init");
        var before = keys().latest();
        var oldNextPrivate = vault.items.get(KeysCommand.NEXT).getPrivate();

        assertThat(run("rotate")).isZero();

        var after = keys().latest();
        assertThat(after.current()).isEqualTo(before.next());
        assertThat(ci.key).hasValue(oldNextPrivate);
        assertThat(vault.publicKey(KeysCommand.NEXT)).isEqualTo(after.next());
        assertThat(vault.archived).containsExactly(KeysCommand.NEXT);
        assertThat(vault.items).doesNotContainKey(KeysCommand.NEXT + KeysCommand.PENDING);
    }

    @Test
    void rotationRefusesUncommittedKeys() throws Exception {
        run("init");
        gitStatus = " M trust/keys.json";

        assertThat(run("rotate")).isEqualTo(1);

        assertThat(err.toString()).contains("isn't committed");
        assertThat(keys().latest().number()).isZero();
    }

    @Test
    void breakGlassNeedsConfirmation() throws Exception {
        run("init");
        input = "no\n";

        assertThat(run("break-glass")).isEqualTo(1);

        assertThat(err.toString()).contains("Cancelled");
        assertThat(keys().latest().number()).isZero();
    }

    @Test
    void breakGlassReplacesEveryKey() throws Exception {
        run("init");
        var before = keys().latest();
        input = "break glass\n";

        assertThat(run("break-glass")).isZero();

        var after = keys().latest();
        assertThat(after.signedBy()).isEqualTo(KeyChain.SignedBy.BREAK_GLASS);
        assertThat(List.of(after.current(), after.next(), after.breakGlass()))
                .doesNotContainAnyElementsOf(List.of(before.current(), before.next(), before.breakGlass()));
        assertThat(ciKeyMatches(after.current())).isTrue();
        assertThat(vault.publicKey(KeysCommand.BREAK_GLASS)).isEqualTo(after.breakGlass());
    }

    private int run(String command) {
        Shell git = (args, stdin) -> args.contains("status") ? gitStatus : "";
        return SigningTool.commandLine(Map.of(), vault, ci, git, new BufferedReader(new StringReader(input)))
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(err, true))
                .execute("keys", "--keys", keysFile().toString(), command);
    }

    private Path keysFile() {
        return folder.resolve("trust/keys.json");
    }

    private KeyChain keys() throws Exception {
        return KeyChain.read(Files.readAllBytes(keysFile()));
    }

    /** Whether CI's private key belongs to the given public key: something it signs, that key verifies. */
    private boolean ciKeyMatches(String publicKey) {
        var data = new byte[] {1};
        return Keys.verify(Keys.decodePublic(publicKey), data, Keys.sign(ci.key.orElseThrow(), data));
    }

    private static final class FakeVault implements Vault {
        final Map<String, KeyPair> items = new HashMap<>();
        final List<String> archived = new ArrayList<>();

        @Override
        public boolean has(String item) {
            return items.containsKey(item);
        }

        @Override
        public String read(String item) {
            return Keys.encode(items.get(item).getPrivate());
        }

        @Override
        public void save(String item, KeyPair keys) {
            items.put(item, keys);
        }

        @Override
        public void replaceWithPending(String item) {
            archived.add(item);
            items.put(item, items.remove(item + KeysCommand.PENDING));
        }

        String publicKey(String item) {
            return Keys.encode(items.get(item).getPublic());
        }
    }

    private static final class FakeCi implements CiSecrets {
        Optional<PrivateKey> key = Optional.empty();

        @Override
        public boolean hasSigningKey() {
            return key.isPresent();
        }

        @Override
        public void setSigningKey(PrivateKey key) {
            this.key = Optional.of(key);
        }
    }
}
