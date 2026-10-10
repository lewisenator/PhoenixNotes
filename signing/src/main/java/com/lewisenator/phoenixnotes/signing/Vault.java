package com.lewisenator.phoenixnotes.signing;

import java.io.IOException;
import java.security.KeyPair;
import java.util.List;
import java.util.Map;

/** Where the offline private keys (next and break glass) live: a 1Password vault. */
interface Vault {

    boolean has(String item) throws IOException;

    /** The item's private key, base64. */
    String read(String item) throws IOException;

    void save(String item, KeyPair keys) throws IOException;

    /** Moves the item to 1Password's archive, where it can still be restored. */
    void archive(String item) throws IOException;

    /** A 1Password vault, through the {@code op} command. Values go in on stdin, never as arguments. */
    static Vault onePassword(Shell shell, String vault) {
        return new Vault() {
            @Override
            public boolean has(String item) {
                try {
                    shell.run(List.of("op", "item", "get", item, "--vault", vault), "");
                    return true;
                } catch (IOException e) {
                    return false;
                }
            }

            @Override
            public String read(String item) throws IOException {
                return shell.run(List.of("op", "read", "op://" + vault + "/" + item + "/credential"), "")
                        .strip();
            }

            @Override
            public void save(String item, KeyPair keys) throws IOException {
                var json = Release.JSON.writeValueAsString(Map.of(
                        "title",
                        item,
                        "category",
                        "API_CREDENTIAL",
                        "fields",
                        List.of(
                                Map.of(
                                        "id", "credential",
                                        "type", "CONCEALED",
                                        "label", "private key",
                                        "value", Keys.encode(keys.getPrivate())),
                                Map.of(
                                        "id", "publicKey",
                                        "type", "STRING",
                                        "label", "public key",
                                        "value", Keys.encode(keys.getPublic())))));
                shell.run(List.of("op", "item", "create", "--vault", vault, "-"), json);
            }

            @Override
            public void archive(String item) throws IOException {
                shell.run(List.of("op", "item", "delete", item, "--vault", vault, "--archive"), "");
            }
        };
    }
}
