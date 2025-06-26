package javafxtest.testjavafx;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class BaseDeDonnee {
    private static HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://yamabiko.proxy.rlwy.net:17088/railway");
        config.setUsername("root");
        config.setPassword("LtmzTbDYljnkvzhtOuHFyswIiuGBlyLb");

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(900000);
        dataSource = new HikariDataSource(config);
    }

    public static Connection seConnecter() throws SQLException {
        return dataSource.getConnection();
    }
}