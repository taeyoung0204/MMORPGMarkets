package market.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class Database {
    private Database() {
    }

    public static Connection open() throws SQLException {
        Properties config = new Properties();
        try (InputStream input = Database.class.getResourceAsStream("/db.local.properties")) {
            if (input == null) {
                throw new SQLException("db.local.properties 파일이 필요합니다.");
            }
            config.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (IOException | ClassNotFoundException e) {
            throw new SQLException("DB 설정과 Oracle JDBC 라이브러리를 확인하세요.", e);
        }
        return DriverManager.getConnection(config.getProperty("db.url"),
                config.getProperty("db.user"), config.getProperty("db.password"));
    }
}
