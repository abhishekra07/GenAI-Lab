package com.genailab.config;

import com.pgvector.PGvector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Registers pgvector type with the PostgreSQL JDBC driver at startup.
 *
 * PGvector.addVectorType(Connection) registers the vector type mapping
 * globally in the driver — one call at startup covers all pool connections.
 */
@Configuration
@Slf4j
public class PgVectorConfig {

    @Autowired
    private DataSource dataSource;

    @PostConstruct
    public void registerPgVectorType() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            PGvector.addVectorType(conn);
            log.info("PGvector type registered with PostgreSQL JDBC driver");
        }
    }
}