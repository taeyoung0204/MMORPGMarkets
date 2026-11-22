package market.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordUtil {
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
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(),
                parts[2].getBytes(StandardCharsets.UTF_8), iterations, 256);
        try {
            byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(expected, actual);
        } finally {
            spec.clearPassword();
        }
    }
}
