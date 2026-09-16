package com.chatdiet.omron;

/** Summary of a completed OMRON CSV import run. */
public record OmronImportResult(int filesImported, int rowsImported) {
}
