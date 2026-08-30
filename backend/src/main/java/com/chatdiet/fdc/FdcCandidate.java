package com.chatdiet.fdc;

/**
 * A lightweight FDC search result - name and id only, no nutrition. FDC's role under the alias
 * resolver is recall, not automated selection: every result is a legitimate candidate for a human
 * to pick from, so nothing is filtered or pre-fetched here. Fetch nutrition for a selected
 * candidate via {@link FdcClient#fetchDetail}.
 */
public record FdcCandidate(String description, long fdcId) {
}
