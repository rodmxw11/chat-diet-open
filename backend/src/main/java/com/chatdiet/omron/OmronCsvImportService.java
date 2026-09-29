package com.chatdiet.omron;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Imports OMRON blood-pressure-monitor CSV exports ({@code Date,Time,Systolic (mmHg),Diastolic
 * (mmHg),Pulse (bpm),...}) into {@code omron_reading}. Rows are upserted on the unique {@code
 * timestamp} column (date + time combined, minute precision - the device itself won't produce two
 * readings a minute apart), so re-running the import against a freshly re-downloaded report that
 * covers an overlapping date range - the normal workflow, since each export runs from a fixed
 * start date through "today" - never creates duplicates.
 */
@Service
public class OmronCsvImportService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMM d yyyy h:mm a")
            .toFormatter(Locale.ENGLISH);

    private static final String UPSERT_SQL = """
            INSERT INTO omron_reading (timestamp, systolic, diastolic, bpm)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(timestamp) DO UPDATE SET
                systolic = excluded.systolic,
                diastolic = excluded.diastolic,
                bpm = excluded.bpm
            """;

    private final JdbcTemplate jdbcTemplate;

    public OmronCsvImportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Imports every {@code .csv} file directly under {@code directory}. */
    public OmronImportResult importDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            return new OmronImportResult(0, 0);
        }
        try (var files = Files.list(directory)) {
            var csvFiles = files
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                    .toList();
            var rows = 0;
            for (var file : csvFiles) {
                rows += importFile(file);
            }
            return new OmronImportResult(csvFiles.size(), rows);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Imports a single OMRON CSV export file, upserting each row by timestamp.
     *
     * @return the number of data rows read (imported or re-imported)
     */
    public int importFile(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var rows = parseRows(lines);
        rows.forEach(this::upsert);
        return rows.size();
    }

    /**
     * Imports one uploaded OMRON export (the About page's Import BP button). Unlike the directory
     * import, it checks the header first and parses every row before writing any, all in one
     * transaction - a wrong or damaged file changes nothing instead of half-importing.
     *
     * @throws IllegalArgumentException if it isn't an OMRON blood-pressure export or a row is
     *                                  malformed; the message says which, for the user
     */
    @Transactional
    public OmronUploadResult importUpload(String csvText) {
        var lines = csvText.strip().lines().toList();
        var header = lines.isEmpty() ? "" : lines.get(0).replace("\uFEFF", "").trim();
        if (!header.toLowerCase(Locale.ROOT).startsWith("date,time,systolic")) {
            throw new IllegalArgumentException("That doesn't look like an OMRON blood pressure export - "
                    + "expected a header starting \"Date,Time,Systolic\".");
        }
        var rows = parseRows(lines);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("The file has no readings in it.");
        }
        var before = countReadings();
        rows.forEach(this::upsert);
        var from = rows.stream().map(ParsedRow::timestamp).min(LocalDateTime::compareTo).orElseThrow();
        var to = rows.stream().map(ParsedRow::timestamp).max(LocalDateTime::compareTo).orElseThrow();
        return new OmronUploadResult(rows.size(), (int) (countReadings() - before), from, to);
    }

    private record ParsedRow(LocalDateTime timestamp, int systolic, int diastolic, Integer bpm) {
    }

    /** Parses every data row (skipping the header and blank or short lines) before anything is written. */
    private static List<ParsedRow> parseRows(List<String> lines) {
        var rows = new ArrayList<ParsedRow>();
        for (var i = 1; i < lines.size(); i++) { // skip the header row
            var line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            var fields = splitCsvLine(line);
            if (fields.length < 5) {
                continue;
            }
            try {
                rows.add(new ParsedRow(
                        LocalDateTime.parse(fields[0].trim() + " " + fields[1].trim(), TIMESTAMP_FORMAT),
                        Integer.parseInt(fields[2].trim()),
                        Integer.parseInt(fields[3].trim()),
                        parseNullableInt(fields[4].trim())));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Line " + (i + 1) + " isn't a valid reading: " + line.strip(), e);
            }
        }
        return rows;
    }

    private void upsert(ParsedRow row) {
        jdbcTemplate.update(UPSERT_SQL, row.timestamp(), row.systolic(), row.diastolic(), row.bpm());
    }

    private long countReadings() {
        var count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM omron_reading", Long.class);
        return count == null ? 0 : count;
    }

    private static Integer parseNullableInt(String value) {
        return (value.isEmpty() || value.equals("-")) ? null : Integer.parseInt(value);
    }

    // OMRON's export always quotes free-text fields (Notes) that might contain a comma, so a
    // naive split(",") would misalign columns for rows with a note - this keeps quoted commas
    // intact.
    private static String[] splitCsvLine(String line) {
        return line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
    }
}
