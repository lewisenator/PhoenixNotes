package com.lewisenator.phoenixnotes.signing;

/** Signed data that failed a check: a bad signature, malformed content, or a key we don't trust. */
public final class UntrustedException extends Exception {

    public UntrustedException(String message) {
        super(message);
    }

    public UntrustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
