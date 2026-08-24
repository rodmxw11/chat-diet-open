package com.chatdiet.fooditem;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.List;
import java.util.Optional;

/**
 * Holds the food-item picker options for the current chat HTTP request, if
 * list_food_items_for_shopping was invoked. Mirrors {@code ChartResultContext}.
 */
@Component
@RequestScope
public class FoodItemPickerContext {

    private List<FoodItemOption> options;

    public void setOptions(List<FoodItemOption> options) {
        this.options = options;
    }

    public Optional<List<FoodItemOption>> options() {
        return Optional.ofNullable(options);
    }
}
