package com.lewisenator.phoenixnotes.signing;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import lombok.SneakyThrows;

/**
 * Ed25519 keys and signatures, as base64 text. Private keys use the standard PKCS#8 encoding and
 * public keys use X.509, which the JDK reads natively.
 */
public final class Keys {

    private static final String ALGORITHM = "Ed25519";

    private Keys() {}

    @SneakyThrows(NoSuchAlgorithmException.class) // Every JDK supports Ed25519.
    public static KeyPair generate() {
        return KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
    }

    public static String encode(Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static PrivateKey decodePrivate(String base64) {
        try {
            var spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64.strip()));
            return KeyFactory.getInstance(ALGORITHM).generatePrivate(spec);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Not an Ed25519 private key", e);
        }
    }

    public static PublicKey decodePublic(String base64) {
        try {
            var spec = new X509EncodedKeySpec(Base64.getDecoder().decode(base64.strip()));
            return KeyFactory.getInstance(ALGORITHM).generatePublic(spec);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Not an Ed25519 public key", e);
        }
    }

    /** Signs the data; returns the signature as base64. */
    @SneakyThrows(GeneralSecurityException.class) // Only possible with a non-Ed25519 key.
    public static String sign(PrivateKey key, byte[] data) {
        var signature = Signature.getInstance(ALGORITHM);
        signature.initSign(key);
        signature.update(data);
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    /** Whether the base64 signature is the key's signature of the data. Malformed input is just false. */
    @SneakyThrows(GeneralSecurityException.class) // Only possible with a non-Ed25519 key.
    public static boolean verify(PublicKey key, byte[] data, String signature) {
        var verifier = Signature.getInstance(ALGORITHM);
        verifier.initVerify(key);
        verifier.update(data);
        try {
            return verifier.verify(Base64.getDecoder().decode(signature.strip()));
        } catch (SignatureException | IllegalArgumentException e) {
            return false;
        }
    }
}
