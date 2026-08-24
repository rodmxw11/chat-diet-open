package com.chatdiet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.relational.core.dialect.Dialect;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Registers the converters needed to read {@code DATE}/{@code DATETIME} columns back through
 * Spring Data JDBC against SQLite. The sqlite-jdbc driver has no native DATE/TIMESTAMP type - it
 * stores/returns these as either an epoch-millis {@link Long} or an ISO-8601 {@link String}
 * depending on query shape, and Spring Boot's default Jsr310 converters only handle one of those
 * shapes, so both directions need an explicit converter here.
 *
 * <p>No writing converter for {@link LocalDate} is registered, deliberately: entity persistence
 * (via {@code save()}) and {@code @Query}-annotated repository methods bind {@link LocalDate}
 * parameters through two different code paths in Spring Data JDBC, and only the former honors a
 * custom {@code WritingConverter} registered here. Adding one made {@code save()} write ISO-8601
 * text while {@code @Query} methods kept binding the driver's default epoch-millis representation
 * for their {@code :param} placeholders - breaking every {@code WHERE date_column = :param}
 * lookup. Leaving both paths on the driver's default (epoch-millis) keeps them consistent with
 * each other; {@link com.chatdiet.sql.ReadOnlySqlExecutor}, which runs hand-written SQL outside
 * Spring Data JDBC entirely, binds its own {@code DATE}/{@code DATETIME} parameters to match this
 * same representation rather than assuming ISO-8601 text.
 *
 * <p>Built via {@link JdbcCustomConversions#of(Dialect, List)}, not {@code new
 * JdbcCustomConversions(...)} - the latter drops Spring Boot's required default Jsr310
 * converters and breaks the whole application context.
 *
 * <p>Spring Data JDBC ships no built-in SQLite {@link Dialect}, so resolving one requires
 * {@link SqliteJdbcDialectProvider} (registered via {@code META-INF/spring.factories}) rather than
 * a {@code Dialect} bean defined here directly: Spring Boot's auto-configuration already defines
 * an unconditional {@code jdbcDialect} bean that calls {@code DialectResolver} itself, so a second
 * bean of the same name would conflict with it instead of replacing it.
 */
@Configuration
public class JdbcDialectConfig {

    @Bean
    public JdbcCustomConversions jdbcCustomConversions(Dialect jdbcDialect) {
        return JdbcCustomConversions.of(jdbcDialect, List.of(
                new LongToLocalDateConverter(),
                new LongToLocalDateTimeConverter(),
                new StringToLocalDateConverter(),
                new StringToLocalDateTimeConverter()));
    }

    @ReadingConverter
    static class LongToLocalDateConverter implements Converter<Long, LocalDate> {
        @Override
        public LocalDate convert(Long epochMillis) {
            return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate();
        }
    }

    @ReadingConverter
    static class LongToLocalDateTimeConverter implements Converter<Long, LocalDateTime> {
        @Override
        public LocalDateTime convert(Long epochMillis) {
            return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
    }

    @ReadingConverter
    static class StringToLocalDateConverter implements Converter<String, LocalDate> {
        @Override
        public LocalDate convert(String value) {
            return LocalDate.parse(value);
        }
    }

    @ReadingConverter
    static class StringToLocalDateTimeConverter implements Converter<String, LocalDateTime> {
        @Override
        public LocalDateTime convert(String value) {
            try {
                return LocalDateTime.parse(value);
            } catch (Exception e) {
                // A bare date string (e.g. from a column whose value happens to look like just a
                // date) falls back to midnight of that date rather than failing the conversion.
                return LocalDate.parse(value).atStartOfDay();
            }
        }
    }
}
