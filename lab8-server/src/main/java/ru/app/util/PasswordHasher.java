package ru.app.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for hashing passwords using the SHA-1 algorithm.
 *
 * <p>Provides a static method to compute the SHA-1 hash of a given password string and return it as
 * a hexadecimal string. The resulting hash is always 40 characters long.
 *
 * <p>This class cannot be instantiated.
 */
public final class PasswordHasher {

  /** Private constructor to prevent instantiation. */
  private PasswordHasher() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /**
   * Computes the SHA-1 hash of the given password.
   *
   * @param password the plaintext password to hash (must not be {@code null})
   * @return the SHA-1 hash as a 40-character lowercase hexadecimal string
   * @throws IllegalArgumentException if {@code password} is {@code null}
   * @throws RuntimeException if the SHA-1 algorithm is not available in the environment
   */
  public static String hash(String password) {
    if (password == null) throw new IllegalArgumentException("'password' can't be null.");
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] hashBytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
      return bytesToHex(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-1 algorithm not available", e);
    }
  }

  /**
   * Converts a byte array to a lowercase hexadecimal string.
   *
   * @param bytes the byte array to convert
   * @return the hexadecimal representation of the byte array
   */
  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) sb.append(String.format("%02x", b));
    return sb.toString();
  }
}
