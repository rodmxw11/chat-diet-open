package com.chatdiet.about;

import com.chatdiet.chat.ChatCostCalculator;
import com.chatdiet.chat.ChatMessageRepository;
import com.chatdiet.day.DayBoundaryService;
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

    /** Trailing window for the AI cost figure, counting today. */
    static final int AI_COST_DAYS = 30;

    private final ConfigurableApplicationContext applicationContext;
    private final Optional<BuildProperties> buildProperties;
    private final String datasourceUrl;
    private final int dayRolloverHour;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatCostCalculator chatCostCalculator;
    private final DayBoundaryService dayBoundaryService;

    public AboutService(ConfigurableApplicationContext applicationContext,
                         Optional<BuildProperties> buildProperties,
                         @Value("${spring.datasource.url}") String datasourceUrl,
                         @Value("${chat-diet.day-rollover-hour:4}") int dayRolloverHour,
                         ChatMessageRepository chatMessageRepository,
                         ChatCostCalculator chatCostCalculator,
                         DayBoundaryService dayBoundaryService) {
        this.applicationContext = applicationContext;
        this.buildProperties = buildProperties;
        this.datasourceUrl = datasourceUrl;
        this.dayRolloverHour = dayRolloverHour;
        this.chatMessageRepository = chatMessageRepository;
        this.chatCostCalculator = chatCostCalculator;
        this.dayBoundaryService = dayBoundaryService;
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
                dayRolloverHour,
                MemoryStats.current(),
                aiCost());
    }

    private AiCostSummary aiCost() {
        var to = dayBoundaryService.today();
        var from = to.minusDays(AI_COST_DAYS - 1);
        var cost = chatCostCalculator.costFor(chatMessageRepository.findByMetabolicDateBetween(from, to));
        return new AiCostSummary(from, to, cost.haikuCostUsd(), cost.opusCostUsd());
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
