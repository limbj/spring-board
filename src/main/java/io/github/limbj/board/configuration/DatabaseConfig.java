package io.github.limbj.board.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/** Database credentials are supplied at runtime; only the local H2 schema is bootstrapped. */
@Configuration
@PropertySource(value = "classpath:application.properties", encoding = "UTF-8")
public class DatabaseConfig {
    @Bean(destroyMethod = "close")
    public DataSource dataSource(Environment environment) throws IOException {
        String profile = setting(environment, "profile", "SPRING_BOARD_DB_PROFILE", "h2").toLowerCase(Locale.ROOT);
        if (!profile.equals("h2") && !profile.equals("oracle")) {
            throw new IllegalArgumentException("SPRING_BOARD_DB_PROFILE must be h2 or oracle");
        }
        String url = setting(environment, "url", "SPRING_BOARD_DB_URL", "");
        String username = setting(environment, "user", "SPRING_BOARD_DB_USER", profile.equals("h2") ? "sa" : "");
        String password = setting(environment, "password", "SPRING_BOARD_DB_PASSWORD", "");
        if (profile.equals("h2")) {
            if (url.isBlank()) {
                String directory = environment.getProperty("springboard.dataDir",
                        environment.getProperty("SPRING_BOARD_DATA_DIR", "./data"));
                Path data = Path.of(directory).toAbsolutePath().normalize();
                Files.createDirectories(data);
                String databasePath = data.resolve("spring-board").toString().replace('\\', '/');
                if (databasePath.contains(";")) throw new IllegalArgumentException("Data directory cannot contain a semicolon");
                url = "jdbc:h2:file:" + databasePath + ";MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_ON_EXIT=FALSE";
            }
            if (!url.startsWith("jdbc:h2:")) throw new IllegalArgumentException("H2 profile requires an H2 JDBC URL");
            String upperUrl = url.toUpperCase(Locale.ROOT);
            if (upperUrl.contains(";MODE=") && !upperUrl.matches(".*;MODE=ORACLE(?:;.*)?$")) {
                throw new IllegalArgumentException("H2 profile requires Oracle compatibility mode");
            }
            if (!upperUrl.contains(";MODE=")) url += ";MODE=Oracle";
            if (!upperUrl.contains(";DEFAULT_NULL_ORDERING=")) url += ";DEFAULT_NULL_ORDERING=HIGH";
        } else {
            if (!url.startsWith("jdbc:oracle:") || username.isBlank() || password.isBlank()) {
                throw new IllegalArgumentException("Oracle profile requires SPRING_BOARD_DB_URL, SPRING_BOARD_DB_USER and SPRING_BOARD_DB_PASSWORD");
            }
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("spring-board-db");
        config.setDriverClassName(profile.equals("h2") ? "org.h2.Driver" : "oracle.jdbc.OracleDriver");
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(environment.getProperty("app.db.pool-size", Integer.class, 5));
        config.setMinimumIdle(1);
        config.setConnectionTimeout(environment.getProperty("app.db.connection-timeout-ms", Long.class, 10_000L));
        HikariDataSource source = new HikariDataSource(config);
        try {
            if (profile.equals("h2")) {
                ResourceDatabasePopulator bootstrap = new ResourceDatabasePopulator(new ClassPathResource("db/schema-h2.sql"));
                bootstrap.setSqlScriptEncoding("UTF-8");
                bootstrap.execute(source);
            }
            return source;
        } catch (RuntimeException exception) {
            source.close();
            throw exception;
        }
    }

    private static String setting(Environment environment, String suffix, String environmentKey, String fallback) {
        return environment.getProperty("springboard.db." + suffix,
                environment.getProperty(environmentKey, environment.getProperty("app.db." + suffix, fallback)));
    }
}
