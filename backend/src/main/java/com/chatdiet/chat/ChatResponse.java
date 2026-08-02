package com.chatdiet.chat;

import com.chatdiet.chart.ChartSeries;
import com.chatdiet.sql.SqlAnswer;

import java.util.List;

/**
 * The response body for a chat turn.
 *
 * @param chartSeries non-null only when the model invoked {@code show_chart} during this turn
 * @param sqlAnswer   non-null only when the model invoked a SQL query tool during this turn
 */
public record ChatResponse(String reply, List<ChartSeries> chartSeries, SqlAnswer sqlAnswer) {

    /** Convenience constructor for a plain reply with no chart or SQL result attached. */
    public ChatResponse(String reply) {
        this(reply, null, null);
    }
}
