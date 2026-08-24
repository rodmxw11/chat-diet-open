package com.chatdiet.config;

import org.springframework.data.jdbc.core.dialect.DialectResolver;
import org.springframework.data.relational.core.dialect.AnsiDialect;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcOperations;

import java.util.Optional;

/**
 * Supplies {@link AnsiDialect} for a SQLite connection. Spring Data JDBC ships dialects for
 * H2/Postgres/MySQL/SQL Server/Oracle/HSQL/MariaDB/DB2 but not SQLite, so without this,
 * {@link DialectResolver} throws {@code NoDialectException} at startup. SQLite's SQL surface
 * (standard {@code LIMIT}/{@code OFFSET}, no dialect-specific locking/upsert syntax this app
 * needs) is adequately covered by the ANSI dialect.
 *
 * <p>Registered via {@code META-INF/spring.factories} under
 * {@code DialectResolver.JdbcDialectProvider} - {@code DialectResolver} aggregates every provider
 * found on the classpath and asks each in turn, so this is additive alongside Spring Data JDBC's
 * own {@code DefaultDialectProvider} rather than replacing it.
 */
public class SqliteJdbcDialectProvider implements DialectResolver.JdbcDialectProvider {

    @Override
    public Optional<Dialect> getDialect(JdbcOperations operations) {
        return operations.execute((ConnectionCallback<Optional<Dialect>>) connection -> {
            var productName = connection.getMetaData().getDatabaseProductName();
            return productName != null && productName.toLowerCase().contains("sqlite")
                    ? Optional.of(AnsiDialect.INSTANCE)
                    : Optional.empty();
        });
    }
}
