package com.chatdiet.chat;

import com.chatdiet.chart.ChartSeries;

import java.util.List;

public record ChatResponse(String reply, List<ChartSeries> chartSeries) {

    public ChatResponse(String reply) {
        this(reply, null);
    }
}
