package com.lewisenator.phoenixnotes.signing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.lewisenator.phoenixnotes.signing.KeyChain.Generation;
import com.lewisenator.phoenixnotes.signing.KeyChain.SignedBy;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeyChainTest {

    private final KeyPair current = Keys.generate();
    private final KeyPair next = Keys.generate();
    private final KeyPair breakGlass = Keys.generate();
    private final KeyChain chain;

    KeyChainTest() throws UntrustedException {
        chain = KeyChain.start(breakGlass.getPrivate(), encode(current), encode(next), encode(breakGlass));
    }

    @Test
    void startsTrustingTheCurrentKey() throws Exception {
        var read = KeyChain.read(chain.toJson());

        assertThat(read).isEqualTo(chain);
        assertThat(read.currentKey()).isEqualTo(current.getPublic());
        assertThat(read.installedVersionKeys()).containsExactly(current.getPublic());
    }

    @Test
    void rotationPromotesNextAndKeepsOlderVersionsTrusted() throws Exception {
        var rotated = KeyChain.read(
                chain.rotate(next.getPrivate(), encode(Keys.generate())).toJson());

        assertThat(rotated.currentKey()).isEqualTo(next.getPublic());
        assertThat(rotated.installedVersionKeys()).containsExactly(current.getPublic(), next.getPublic());
    }

    @Test
    void theCiKeyCannotRotate() {
        // Pre-rotation: only the offline next key can authorize the next generation.
        assertThatExceptionOfType(UntrustedException.class)
                .isThrownBy(() -> chain.rotate(current.getPrivate(), encode(Keys.generate())))
                .withMessageContaining("next key");
    }

    @Test
    void anAppCatchesUpAcrossSeveralRotations() throws Exception {
        var nextAfterNext = Keys.generate();
        var published = chain.rotate(next.getPrivate(), encode(nextAfterNext))
                .rotate(nextAfterNext.getPrivate(), encode(Keys.generate()));

        var caughtUp = chain.extend(KeyChain.read(published.toJson()));

        assertThat(caughtUp.latest().number()).isEqualTo(2);
        assertThat(caughtUp.currentKey()).isEqualTo(nextAfterNext.getPublic());
    }

    @Test
    void breakGlassReplacesEveryKeyAndDistrustsOlderVersions() throws Exception {
        var newCurrent = Keys.generate();
        var replaced = chain.rotate(next.getPrivate(), encode(Keys.generate()))
                .breakGlass(
                        breakGlass.getPrivate(), encode(newCurrent), encode(Keys.generate()), encode(Keys.generate()));

        assertThat(KeyChain.read(replaced.toJson()).installedVersionKeys()).containsExactly(newCurrent.getPublic());
    }

    @Test
    void breakGlassNeedsTheBreakGlassKey() {
        assertThatExceptionOfType(UntrustedException.class)
                .isThrownBy(() -> chain.breakGlass(
                        next.getPrivate(), encode(Keys.generate()), encode(Keys.generate()), encode(Keys.generate())))
                .withMessageContaining("break-glass key");
    }

    @Test
    void aRotationCannotReplaceTheBreakGlassKey() {
        var forged = forge(1, encode(next), encode(Keys.generate()), encode(Keys.generate()), next);

        assertThatExceptionOfType(UntrustedException.class)
                .isThrownBy(() -> chain.extend(forged))
                .withMessageContaining("changes more than a rotation may");
    }

    @Test
    void rejectsTamperedAndSkippedGenerations() throws Exception {
        var rotated = chain.rotate(next.getPrivate(), encode(Keys.generate()));
        var tampered = new KeyChain(List.of(
                chain.latest(),
                new Generation(
                        1,
                        encode(next),
                        encode(Keys.generate()),
                        encode(breakGlass),
                        SignedBy.NEXT,
                        rotated.latest().signature())));
        var skipped = forge(2, encode(next), encode(Keys.generate()), encode(breakGlass), next);

        assertThatExceptionOfType(UntrustedException.class).isThrownBy(() -> chain.extend(tampered));
        assertThatExceptionOfType(UntrustedException.class)
                .isThrownBy(() -> chain.extend(skipped))
                .withMessageContaining("followed by 2");
    }

    @Test
    void rejectsAnAttackersOwnChain() throws Exception {
        // Valid on its own, but our chain never vouched for it.
        var attacker = Keys.generate();
        var theirs = KeyChain.start(attacker.getPrivate(), encode(attacker), encode(attacker), encode(attacker))
                .rotate(attacker.getPrivate(), encode(attacker));

        assertThatExceptionOfType(UntrustedException.class)
                .isThrownBy(() -> chain.extend(KeyChain.read(theirs.toJson())));
    }

    @Test
    void rejectsInvalidJson() {
        for (var json : List.of("{", "null", "{\"generations\": []}")) {
            assertThatExceptionOfType(UntrustedException.class)
                    .isThrownBy(() -> KeyChain.read(json.getBytes(StandardCharsets.UTF_8)));
        }
    }

    /** Adds a generation signed with any key, skipping the checks the real methods make. */
    private KeyChain forge(int number, String current, String next, String breakGlass, KeyPair signer) {
        var unsigned = new Generation(number, current, next, breakGlass, SignedBy.NEXT, "");
        var generations = new ArrayList<>(chain.generations());
        generations.add(unsigned.withSignature(Keys.sign(signer.getPrivate(), unsigned.signedText())));
        return new KeyChain(generations);
    }

    private static String encode(KeyPair keyPair) {
        return Keys.encode(keyPair.getPublic());
    }
}
