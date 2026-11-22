package market;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public final class Config {
    private Config() {
    }

    public static Properties load() throws IOException {
        Properties config = new Properties();
        try (InputStream input = Config.class.getResourceAsStream("/db.local.properties")) {
            if (input == null) {
                throw new IOException("db.local.properties 파일이 필요합니다.");
            }
            config.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        return config;
    }
}
