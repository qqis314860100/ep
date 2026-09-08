package com.tianshu.assets.system.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 登录密码哈希（PBKDF2-HMAC-SHA256，演示环境）。格式：{saltB64}:{iterations}:{hashB64}。
 * 生产建议升级为 BCrypt/Argon2 或接入 IdP（见 D1 计划文档）。
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 60_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {}

    public static String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        var salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        try {
            return encode(salt, ITERATIONS, derive(rawPassword, salt, ITERATIONS));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("密码哈希算法不可用", exception);
        }
    }

    public static boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) return false;
        var parts = storedHash.split(":");
        if (parts.length != 3) return false;
        try {
            var salt = Base64.getDecoder().decode(parts[0]);
            var iterations = Integer.parseInt(parts[1]);
            var expected = Base64.getDecoder().decode(parts[2]);
            var actual = derive(rawPassword, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException | NoSuchAlgorithmException exception) {
            return false;
        }
    }

    private static String encode(byte[] salt, int iterations, byte[] hash) {
        return Base64.getEncoder().encodeToString(salt) + ":" + iterations + ":"
                + Base64.getEncoder().encodeToString(hash);
    }

    private static byte[] derive(String password, byte[] salt, int iterations)
            throws NoSuchAlgorithmException {
        try {
            var spec = new javax.crypto.spec.PBEKeySpec(
                    password.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
            var factory = javax.crypto.SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new NoSuchAlgorithmException(exception.getMessage());
        }
    }
}
