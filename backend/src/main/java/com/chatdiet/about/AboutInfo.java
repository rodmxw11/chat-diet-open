package com.chatdiet.about;

import java.time.Instant;

/**
 * App/runtime metadata for the About page.
 *
 * @param version           the jar's version (from build.gradle), or {@code null} if
 *                          build-info.properties wasn't generated (e.g. running via IDE run
 *                          config rather than a Gradle build)
 * @param gitCommit         short commit hash the running build was built from, or {@code null}
 * @param buildTime         when the jar was built, or {@code null}
 * @param startupTime       when the Spring application context started
 * @param uptimeSeconds     seconds elapsed since {@code startupTime}
 * @param javaRuntime       the JVM version running the app
 * @param os                operating system name and version
 * @param databaseSizeBytes size of the SQLite database file on disk, or {@code null} if it
 *                          couldn't be read
 * @param dayRolloverHour   the configured metabolic-day rollover hour
 */
public record AboutInfo(
        String appName,
        String version,
        String gitCommit,
        Instant buildTime,
        Instant startupTime,
        long uptimeSeconds,
        String javaRuntime,
        String os,
        Long databaseSizeBytes,
        int dayRolloverHour
) {
}
