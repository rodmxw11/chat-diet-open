package com.chatdiet.shopping;

import com.chatdiet.fooditem.FoodItemOption;
import com.chatdiet.fooditem.FoodItemPickerContext;
import com.chatdiet.fooditem.FoodItemRepository;
import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * IntentTool that lists cached food items (most-used first) for the user to pick from when
 * bulk-adding to the shopping list, stashing the options in the request-scoped
 * {@link FoodItemPickerContext} so {@code ChatController} can attach them to the HTTP response
 * for the frontend's food-item picker to render.
 */
@Component
@IntentTool(
        name = "list_food_items_for_shopping",
        intents = {"manage_shopping"},
        description = "List previously logged food items (most-used first) so the user can pick some to add to the shopping list. Renders an interactive picker inline - do not also list the items in your reply."
)
public class ListFoodItemsForShoppingTool implements Function<ListFoodItemsForShoppingRequest, ToolResult> {

    private final FoodItemRepository foodItemRepository;
    private final FoodItemPickerContext foodItemPickerContext;

    public ListFoodItemsForShoppingTool(FoodItemRepository foodItemRepository,
                                         FoodItemPickerContext foodItemPickerContext) {
        this.foodItemRepository = foodItemRepository;
        this.foodItemPickerContext = foodItemPickerContext;
    }

    @Override
    public ToolResult apply(ListFoodItemsForShoppingRequest request) {
        var options = foodItemRepository.findAllOrderByUseCountDescNameAsc().stream()
                .map(item -> new FoodItemOption(item.id(), item.name(), item.typicalServingG()))
                .toList();
        foodItemPickerContext.setOptions(options);
        return new ToolResult.Success("Food item picker rendered inline.", options);
    }
}
