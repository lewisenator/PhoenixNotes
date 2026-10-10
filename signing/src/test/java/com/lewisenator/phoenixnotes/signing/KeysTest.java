package com.lewisenator.phoenixnotes.signing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import org.junit.jupiter.api.Test;

class KeysTest {

    private final KeyPair keys = Keys.generate();
    private final byte[] data = "data".getBytes(StandardCharsets.UTF_8);

    @Test
    void keysSurviveEncoding() {
        assertThat(Keys.decodePrivate(Keys.encode(keys.getPrivate()))).isEqualTo(keys.getPrivate());
        assertThat(Keys.decodePublic(" " + Keys.encode(keys.getPublic()) + "\n"))
                .isEqualTo(keys.getPublic());
    }

    @Test
    void rejectsSomethingThatIsNotAKey() {
        assertThatIllegalArgumentException().isThrownBy(() -> Keys.decodePublic("not a key"));
        assertThatIllegalArgumentException().isThrownBy(() -> Keys.decodePrivate(Keys.encode(keys.getPublic())));
    }

    @Test
    void verifiesOnlyTheMatchingSignature() {
        var signature = Keys.sign(keys.getPrivate(), data);

        assertThat(Keys.verify(keys.getPublic(), data, signature)).isTrue();
        assertThat(Keys.verify(keys.getPublic(), "other".getBytes(StandardCharsets.UTF_8), signature))
                .isFalse();
        assertThat(Keys.verify(Keys.generate().getPublic(), data, signature)).isFalse();
        assertThat(Keys.verify(keys.getPublic(), data, "not base64!")).isFalse();
    }
}
