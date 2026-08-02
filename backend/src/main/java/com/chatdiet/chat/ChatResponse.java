package com.chatdiet.chat;

import com.chatdiet.chart.ChartSeries;
import com.chatdiet.sql.SqlAnswer;

import java.util.List;

public record ChatResponse(String reply, List<ChartSeries> chartSeries, SqlAnswer sqlAnswer) {

    public ChatResponse(String reply) {
        this(reply, null, null);
    }
}
