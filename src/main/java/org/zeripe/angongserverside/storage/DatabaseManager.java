package org.zeripe.angongserverside.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.zeripe.angongserverside.config.ServerConfig;

import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseManager {
    private HikariDataSource dataSource;
    private final Logger logger;

    public DatabaseManager(Logger logger) {
        this.logger = logger;
    }

    public void init(ServerConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
                config.mysqlHost, config.mysqlPort, config.mysqlDatabase
        ));
        hc.setUsername(config.mysqlUsername);
        hc.setPassword(config.mysqlPassword);
        hc.setMaximumPoolSize(config.mysqlPoolSize);
        hc.setMinimumIdle(2);
        hc.setIdleTimeout(300000);
        hc.setMaxLifetime(600000);
        hc.setConnectionTimeout(10000);
        hc.setPoolName("CDS-HikariPool");

        hc.addDataSourceProperty("cachePrepStmts", "true");
        hc.addDataSourceProperty("prepStmtCacheSize", "250");
        hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hc.addDataSourceProperty("useServerPrepStmts", "true");
        hc.addDataSourceProperty("rewriteBatchedStatements", "true");

        dataSource = new HikariDataSource(hc);
        logger.info("[MySQL] HikariCP 커넥션 풀 초기화 완료 ({})", config.mysqlHost);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("[MySQL] 커넥션 풀 종료");
        }
    }
}
