package com.ramzi.backend.service.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Speichert FinTS-Zugangsdaten als AES-256-GCM-Ciphertext.
 * Der Schlüssel kommt aus der Env-Variable {@code FINTS_ENCRYPTION_KEY}
 * (bzw. später aus Vault über dieselbe Property).
 *
 * <p>GCM ist das Java-Äquivalent zu Fernet: authentifizierte Verschlüsselung,
 * ohne die PIN jemals im Klartext zu persistieren.
 */
@Service
public class CredentialEncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public CredentialEncryptionService(@Value("${fints.encryption-key}") String encryptionKey) {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException("fints.encryption-key / FINTS_ENCRYPTION_KEY must be set");
        }
        this.key = new SecretKeySpec(sha256(encryptionKey), "AES");
    }

    public String encryptCredentials(String loginId, String pin) {
        String login = loginId != null ? loginId : "";
        String secret = pin != null ? pin : "";
        return encrypt(login.length() + ":" + login + secret);
    }

    public CredentialsPayload decryptCredentials(String ciphertext) {
        String plain = decrypt(ciphertext);
        int sep = plain.indexOf(':');
        if (sep < 0) {
            throw new IllegalStateException("Corrupt FinTS credential payload");
        }
        int loginLength = Integer.parseInt(plain.substring(0, sep));
        String loginId = plain.substring(sep + 1, sep + 1 + loginLength);
        String pin = plain.substring(sep + 1 + loginLength);
        return new CredentialsPayload(loginId, pin);
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt FinTS credentials", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            if (decoded.length < IV_BYTES + 16) {
                throw new IllegalStateException("Ciphertext too short");
            }
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to decrypt FinTS credentials", e);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record CredentialsPayload(String loginId, String pin) {}
}
