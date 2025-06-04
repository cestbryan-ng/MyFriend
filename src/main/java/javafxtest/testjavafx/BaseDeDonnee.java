package javafxtest.testjavafx;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class BaseDeDonnee {
    private static HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://192.168.1.102:3306/monapp");
        config.setUsername("Jean_Roland");
        config.setPassword("Papasenegal0");

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(8000);
        config.setIdleTimeout(900000);
        dataSource = new HikariDataSource(config);
    }

    public static Connection seConnecter() throws SQLException {
        return dataSource.getConnection();
    }
}