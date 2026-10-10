package com.lewisenator.phoenixnotes.signing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The op and gh commands we run, recorded instead of executed. */
class ExternalToolsTest {

    private final List<List<String>> commands = new ArrayList<>();
    private final List<String> stdins = new ArrayList<>();
    private String output = "";

    private final Shell recorder = (command, stdin) -> {
        commands.add(command);
        stdins.add(stdin);
        return output;
    };

    @Test
    void savesToOnePasswordWithTheKeyOnlyOnStdin() throws Exception {
        var keys = Keys.generate();
        var privateKey = Keys.encode(keys.getPrivate());

        Vault.onePassword(recorder, "Private").save("Item", keys);

        assertThat(commands).containsExactly(List.of("op", "item", "create", "--vault", "Private", "-"));
        assertThat(stdins.getFirst())
                .contains(privateKey)
                .contains("\"title\":\"Item\"")
                .contains("\"label\":\"private key\"");
        assertThat(commands.getFirst()).noneMatch(argument -> argument.contains(privateKey));
    }

    @Test
    void readsAndArchivesOnePasswordItems() throws Exception {
        output = "the-key\n";
        var vault = Vault.onePassword(recorder, "Private");

        assertThat(vault.read("Item")).isEqualTo("the-key");
        assertThat(vault.has("Item")).isTrue();
        vault.archive("Item");

        assertThat(commands)
                .containsExactly(
                        List.of("op", "read", "op://Private/Item/credential"),
                        List.of("op", "item", "get", "Item", "--vault", "Private"),
                        List.of("op", "item", "delete", "Item", "--vault", "Private", "--archive"));
    }

    @Test
    void aMissingOnePasswordItemIsNotThere() throws Exception {
        Shell failing = (command, stdin) -> {
            throw new IOException("not found");
        };

        assertThat(Vault.onePassword(failing, "Private").has("Item")).isFalse();
    }

    @Test
    void setsTheCiSecretWithTheKeyOnlyOnStdin() throws Exception {
        var key = Keys.generate().getPrivate();

        CiSecrets.gitHub(recorder).setSigningKey(key);

        assertThat(commands).containsExactly(List.of("gh", "secret", "set", CiSecrets.SIGNING_KEY));
        assertThat(stdins).containsExactly(Keys.encode(key));
    }

    @Test
    void findsTheCiSecretByName() throws Exception {
        var ci = CiSecrets.gitHub(recorder);

        output = "OTHER\t2026-10-09\n";
        assertThat(ci.hasSigningKey()).isFalse();
        output = "PHOENIXNOTES_SIGNING_KEY\t2026-10-09\n";
        assertThat(ci.hasSigningKey()).isTrue();
    }
}
