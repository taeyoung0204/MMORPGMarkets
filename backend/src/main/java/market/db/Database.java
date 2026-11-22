package market.db;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import market.Config;

public final class Database {
    private Database() {
    }

    public static Connection open() throws SQLException {
        Properties config;
        try {
            config = Config.load();
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (IOException | ClassNotFoundException e) {
            throw new SQLException("DB 설정과 Oracle JDBC 라이브러리를 확인하세요.", e);
        }
        return DriverManager.getConnection(config.getProperty("db.url"),
                config.getProperty("db.user"), config.getProperty("db.password"));
    }
}
