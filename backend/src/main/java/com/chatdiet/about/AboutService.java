package com.chatdiet.about;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Builds the About page's app/runtime metadata. {@link BuildProperties} is populated from
 * {@code META-INF/build-info.properties}, written at build time by the {@code bootBuildInfo}
 * Gradle task (see build.gradle) - absent (hence {@link Optional}) when running outside a Gradle
 * build, e.g. straight from an IDE run configuration.
 */
@Service
public class AboutService {

    private final ConfigurableApplicationContext applicationContext;
    private final Optional<BuildProperties> buildProperties;
    private final String datasourceUrl;
    private final int dayRolloverHour;

    public AboutService(ConfigurableApplicationContext applicationContext,
                         Optional<BuildProperties> buildProperties,
                         @Value("${spring.datasource.url}") String datasourceUrl,
                         @Value("${chat-diet.day-rollover-hour:4}") int dayRolloverHour) {
        this.applicationContext = applicationContext;
        this.buildProperties = buildProperties;
        this.datasourceUrl = datasourceUrl;
        this.dayRolloverHour = dayRolloverHour;
    }

    public AboutInfo current() {
        var startupTime = Instant.ofEpochMilli(applicationContext.getStartupDate());
        var uptimeSeconds = Duration.between(startupTime, Instant.now()).getSeconds();
        var build = buildProperties.orElse(null);

        return new AboutInfo(
                "chat-diet",
                build != null ? build.getVersion() : null,
                build != null ? build.get("git.commit") : null,
                build != null ? build.getTime() : null,
                startupTime,
                uptimeSeconds,
                Runtime.version().toString(),
                System.getProperty("os.name") + " " + System.getProperty("os.version"),
                databaseSizeBytes(),
                dayRolloverHour);
    }

    // Mirrors ExportService/ReadOnlySqlExecutor's assumption that spring.datasource.url is a
    // plain "jdbc:sqlite:<path>" URL (no query params) - true for every environment this app runs
    // in (see application.yml.example).
    private Long databaseSizeBytes() {
        try {
            var path = Path.of(datasourceUrl.replaceFirst("^jdbc:sqlite:", ""));
            return Files.exists(path) ? Files.size(path) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
