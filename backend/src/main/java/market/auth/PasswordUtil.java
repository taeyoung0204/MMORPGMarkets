package market.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordUtil {
    private static final int ITERATIONS = 600000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    public static boolean verify(String password, String stored) throws GeneralSecurityException {
        if (stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$", -1);
        if (parts.length != 4 || !parts[0].equals("pbkdf2_sha256")) {
            return false;
        }
        int iterations;
        byte[] expected;
        try {
            iterations = Integer.parseInt(parts[1]);
            expected = HexFormat.of().parseHex(parts[3]);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (iterations <= 0 || expected.length != 32 || parts[2].isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(expected, derive(password, parts[2], iterations));
    }

    public static String hash(String password) throws GeneralSecurityException {
        byte[] saltBytes = new byte[16];
        RANDOM.nextBytes(saltBytes);
        String salt = HexFormat.of().formatHex(saltBytes);
        return "pbkdf2_sha256$" + ITERATIONS + "$" + salt + "$"
                + HexFormat.of().formatHex(derive(password, salt, ITERATIONS));
    }

    private static byte[] derive(String password, String salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(),
                salt.getBytes(StandardCharsets.UTF_8), iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
