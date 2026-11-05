package foodassistant.account;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Turns a password into a stored hash. The original password is not kept. */
public final class Passwords {
    private static final int ITERATIONS = 12000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Passwords() {
    }

    public static String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return hex(salt);
    }

    public static String newToken() {
        byte[] token = new byte[32];
        RANDOM.nextBytes(token);
        return hex(token);
    }

    public static String hash(String password, String salt) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), decode(salt), ITERATIONS, HASH_BITS);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return hex(hash);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not hash password", exception);
        }
    }

    public static boolean matches(String password, String salt, String expected) {
        try {
            byte[] actual = decode(hash(password, salt));
            byte[] stored = decode(expected);
            return MessageDigest.isEqual(actual, stored);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            out[i * 2] = HEX[value >>> 4];
            out[i * 2 + 1] = HEX[value & 0x0f];
        }
        return new String(out);
    }

    private static byte[] decode(String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Bad hex");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
