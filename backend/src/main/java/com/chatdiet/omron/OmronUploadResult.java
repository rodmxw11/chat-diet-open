package com.chatdiet.omron;

import java.time.LocalDateTime;

/**
 * Summary of one uploaded OMRON export.
 *
 * @param readings    rows in the file, all upserted
 * @param newReadings how many of those weren't already stored - an export re-downloaded over an
 *                    overlapping date range mostly re-reports readings already imported
 * @param from        the earliest reading in the file
 * @param to          the latest reading in the file
 */
public record OmronUploadResult(int readings, int newReadings, LocalDateTime from, LocalDateTime to) {
}
