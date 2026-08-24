package com.chatdiet.chat;

import com.chatdiet.chart.ChartSeries;
import com.chatdiet.fooditem.FoodItemOption;
import com.chatdiet.sql.SqlAnswer;

import java.util.List;

/**
 * The response body for a chat turn.
 *
 * @param chartSeries      non-null only when the model invoked {@code show_chart} during this turn
 * @param sqlAnswer        non-null only when the model invoked a SQL query tool during this turn
 * @param foodItemOptions  non-null only when the model invoked {@code list_food_items_for_shopping}
 *                         during this turn
 */
public record ChatResponse(String reply, List<ChartSeries> chartSeries, SqlAnswer sqlAnswer,
                            List<FoodItemOption> foodItemOptions) {

    /** Convenience constructor for a plain reply with no chart, SQL, or food-item-picker result attached. */
    public ChatResponse(String reply) {
        this(reply, null, null, null);
    }
}
