package com.lewisenator.phoenixnotes.signing;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import lombok.With;
import tools.jackson.core.JacksonException;

/**
 * The trusted signing keys, published as {@code keys.json} with every release. Each generation names
 * three public keys:
 *
 * <ul>
 *   <li>{@code current} signs releases. Its private key is in CI.
 *   <li>{@code next} becomes current at the next rotation. Its private key is offline, and it signs
 *       the generation that promotes it.
 *   <li>{@code breakGlass} is for emergencies. Its private key is offline, and it can sign a
 *       generation that replaces every key.
 * </ul>
 *
 * A generation is only trusted if the one before it vouches for it, so trust flows forward from the
 * first generation an app was built with.
 */
public record KeyChain(List<Generation> generations) {

    public enum SignedBy {
        NEXT,
        BREAK_GLASS
    }

    public record Generation(
            int number,
            String current,
            String next,
            String breakGlass,
            SignedBy signedBy,
            @With String signature) {

        public Generation {
            List.of(current, next, breakGlass).forEach(Keys::decodePublic);
            if (signedBy == null || signature == null) {
                throw new IllegalArgumentException("signedBy and signature are required");
            }
        }

        /** The exact text that's signed: our own format, so JSON formatting can't change it. */
        byte[] signedText() {
            return String.join("\n", "phoenix-notes-keys", "" + number, current, next, breakGlass, signedBy.name())
                    .getBytes(StandardCharsets.UTF_8);
        }

        boolean isSignedBy(String publicKey) {
            return Keys.verify(Keys.decodePublic(publicKey), signedText(), signature);
        }
    }

    public KeyChain {
        generations = List.copyOf(generations);
        if (generations.isEmpty()) {
            throw new IllegalArgumentException("A key chain needs at least one generation");
        }
    }

    public Generation latest() {
        return generations.getLast();
    }

    /** The key new releases must be signed with. */
    public PublicKey currentKey() {
        return Keys.decodePublic(latest().current());
    }

    /**
     * The keys an installed version may be signed with: every current key since the last break glass.
     * Routine rotations keep older versions trusted; break glass distrusts everything before it.
     */
    public List<PublicKey> installedVersionKeys() {
        var lastBreakGlass = IntStream.range(0, generations.size())
                .filter(i -> generations.get(i).signedBy() == SignedBy.BREAK_GLASS)
                .max()
                .orElse(0);
        return generations.subList(lastBreakGlass, generations.size()).stream()
                .map(Generation::current)
                .distinct()
                .map(Keys::decodePublic)
                .toList();
    }

    /** Adds the generations from a newer copy that come after ours, each checked against the one before. */
    public KeyChain extend(KeyChain newer) throws UntrustedException {
        var result = new ArrayList<>(generations);
        for (var generation : newer.generations()) {
            if (generation.number() > result.getLast().number()) {
                checkFollows(result.getLast(), generation);
                result.add(generation);
            }
        }
        return new KeyChain(result);
    }

    /** A routine rotation: next becomes current, with a new next. Signed with the next private key. */
    public KeyChain rotate(PrivateKey nextKey, String newNext) throws UntrustedException {
        var latest = latest();
        return append(
                new Generation(latest.number() + 1, latest.next(), newNext, latest.breakGlass(), SignedBy.NEXT, ""),
                nextKey);
    }

    /** An emergency: every key replaced. Signed with the break-glass private key. */
    public KeyChain breakGlass(PrivateKey breakGlassKey, String current, String next, String newBreakGlass)
            throws UntrustedException {
        return append(
                new Generation(latest().number() + 1, current, next, newBreakGlass, SignedBy.BREAK_GLASS, ""),
                breakGlassKey);
    }

    public byte[] toJson() {
        return Release.JSON.writeValueAsBytes(this);
    }

    /** Signs a new generation, then checks it like any app would: a wrong private key fails here. */
    private KeyChain append(Generation unsigned, PrivateKey key) throws UntrustedException {
        return extend(new KeyChain(List.of(unsigned.withSignature(Keys.sign(key, unsigned.signedText())))));
    }

    /** The first generation, signed by its own break-glass key. */
    public static KeyChain start(PrivateKey breakGlassKey, String current, String next, String breakGlass)
            throws UntrustedException {
        var unsigned = new Generation(0, current, next, breakGlass, SignedBy.BREAK_GLASS, "");
        var first = unsigned.withSignature(Keys.sign(breakGlassKey, unsigned.signedText()));
        checkFirst(first);
        return new KeyChain(List.of(first));
    }

    /** Parses keys.json and checks that every generation follows from the one before it. */
    public static KeyChain read(byte[] json) throws UntrustedException {
        var chain = parse(json);
        var first = chain.generations().getFirst();
        checkFirst(first);
        return new KeyChain(List.of(first)).extend(chain);
    }

    private static KeyChain parse(byte[] json) throws UntrustedException {
        try {
            var chain = Release.JSON.readValue(json, KeyChain.class);
            if (chain == null) {
                throw new UntrustedException("keys.json is empty");
            }
            return chain;
        } catch (JacksonException e) {
            throw new UntrustedException("keys.json isn't valid: " + e.getOriginalMessage(), e);
        }
    }

    private static void checkFirst(Generation first) throws UntrustedException {
        if (first.number() != 0 || first.signedBy() != SignedBy.BREAK_GLASS || !first.isSignedBy(first.breakGlass())) {
            throw new UntrustedException("The first generation isn't validly signed");
        }
    }

    /** The two rules for trusting a new generation. */
    private static void checkFollows(Generation previous, Generation next) throws UntrustedException {
        if (next.number() != previous.number() + 1) {
            throw new UntrustedException("Generation " + previous.number() + " is followed by " + next.number());
        }
        switch (next.signedBy()) {
            // A rotation must be signed by the previous next key, may only promote it, and can't touch
            // the break-glass key: otherwise whoever holds next could lock out the real owner.
            case NEXT -> {
                if (!next.isSignedBy(previous.next())) {
                    throw new UntrustedException(
                            "Generation " + next.number() + " isn't signed by the trusted next key");
                }
                if (!next.current().equals(previous.next())
                        || !next.breakGlass().equals(previous.breakGlass())) {
                    throw new UntrustedException("Generation " + next.number() + " changes more than a rotation may");
                }
            }
            // Break glass must be signed by the previous break-glass key, and may change everything.
            case BREAK_GLASS -> {
                if (!next.isSignedBy(previous.breakGlass())) {
                    throw new UntrustedException(
                            "Generation " + next.number() + " isn't signed by the trusted break-glass key");
                }
            }
        }
    }
}
