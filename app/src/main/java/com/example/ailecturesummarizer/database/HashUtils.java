package com.example.ailecturesummarizer.database;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for secure password hashing using SHA-256.
 */
public class HashUtils {

    /**
     * Hashes a plain-text password using the SHA-256 algorithm.
     *
     * @param password Plain-text password.
     * @return Hashed hex string representation, or null if input is null.
     */
    public static String hashPassword(String password) {
        if (password == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
