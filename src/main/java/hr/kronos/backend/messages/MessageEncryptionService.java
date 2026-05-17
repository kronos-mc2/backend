package hr.kronos.backend.messages;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MessageEncryptionService {
  private static final String CIPHER = "AES/GCM/NoPadding";
  private static final int GCM_TAG_BITS = 128;
  private static final int NONCE_BYTES = 12;
  private static final String KEY_ID = "v1";

  private final SecretKeySpec key;
  private final SecureRandom secureRandom = new SecureRandom();

  public MessageEncryptionService(@Value("${app.messages.encryption.secret:${auth.jwt.secret}}") String encryptionSecret) {
    this.key = new SecretKeySpec(deriveKey(encryptionSecret), "AES");
  }

  public EncryptedMessage encrypt(String plaintext) {
    if (plaintext == null) {
      return null;
    }

    try {
      byte[] nonce = new byte[NONCE_BYTES];
      secureRandom.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance(CIPHER);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      return new EncryptedMessage(
          Base64.getEncoder().encodeToString(ciphertext),
          Base64.getEncoder().encodeToString(nonce),
          KEY_ID,
          1);
    } catch (GeneralSecurityException exception) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Message encryption failed.");
    }
  }

  public String decrypt(String ciphertext, String nonce, String fallbackPlaintext) {
    if (ciphertext == null || nonce == null) {
      return fallbackPlaintext;
    }

    try {
      Cipher cipher = Cipher.getInstance(CIPHER);
      cipher.init(
          Cipher.DECRYPT_MODE,
          key,
          new GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(nonce)));
      return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException | GeneralSecurityException exception) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Message decryption failed.");
    }
  }

  private byte[] deriveKey(String secret) {
    String normalized = secret == null ? "" : secret.trim();
    if (normalized.length() < 32) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Message encryption secret must be at least 32 characters.");
    }

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update("kronos-message-encryption".getBytes(StandardCharsets.UTF_8));
      return Arrays.copyOf(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)), 32);
    } catch (GeneralSecurityException exception) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Message encryption key derivation failed.");
    }
  }

  public record EncryptedMessage(String ciphertext, String nonce, String keyId, int version) {}
}
