package com.lewisenator.phoenixnotes.signing;

import java.io.IOException;
import java.security.PrivateKey;
import java.util.List;

/** Where CI's signing key (the current key) lives: a GitHub Actions secret. It can be set, never read. */
interface CiSecrets {

    String SIGNING_KEY = "PHOENIXNOTES_SIGNING_KEY";

    boolean hasSigningKey() throws IOException;

    void setSigningKey(PrivateKey key) throws IOException;

    /** This repository's secrets, through the {@code gh} command. The key goes in on stdin. */
    static CiSecrets gitHub(Shell shell) {
        return new CiSecrets() {
            @Override
            public boolean hasSigningKey() throws IOException {
                return shell.run(List.of("gh", "secret", "list"), "")
                        .lines()
                        .anyMatch(line -> line.matches(SIGNING_KEY + "(\\s.*)?")); // The name, then the date.
            }

            @Override
            public void setSigningKey(PrivateKey key) throws IOException {
                shell.run(List.of("gh", "secret", "set", SIGNING_KEY), Keys.encode(key));
            }
        };
    }
}
