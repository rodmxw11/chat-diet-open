package com.chatdiet.shopping;

import com.chatdiet.fooditem.FoodItemRepository;
import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * IntentTool that adds an item to the shopping list, suggesting a store by looking up prior
 * shopping-list history for a matching cached food item first, then falling back to history
 * matched by the item's raw description.
 */
@Component
@IntentTool(
        name = "add_shopping_item",
        intents = {"manage_shopping"},
        description = "Add an item to the shopping list. Automatically suggests a store based on where this item (or a matching cached food) was bought before."
)
public class AddShoppingItemTool implements Function<AddShoppingItemRequest, ToolResult> {

    private final ShoppingItemRepository shoppingItemRepository;
    private final FoodItemRepository foodItemRepository;

    public AddShoppingItemTool(ShoppingItemRepository shoppingItemRepository, FoodItemRepository foodItemRepository) {
        this.shoppingItemRepository = shoppingItemRepository;
        this.foodItemRepository = foodItemRepository;
    }

    /**
     * Saves a new pending shopping list item, resolving a suggested store from prior shopping
     * history for a matching food item, or failing that, from history matching the description.
     *
     * @return a {@link ToolResult.Success} wrapping the saved {@link ShoppingItem}
     */
    @Override
    public ToolResult apply(AddShoppingItemRequest request) {
        var matchedFoodItem = foodItemRepository.findBestMatchByName(request.description()).orElse(null);

        var suggestedStore = matchedFoodItem != null
                ? shoppingItemRepository.findMostCommonStoreByFoodItemId(matchedFoodItem.id()).orElse(null)
                : null;
        if (suggestedStore == null) {
            suggestedStore = shoppingItemRepository.findMostCommonStoreByDescription(request.description())
                    .orElse(null);
        }

        var item = shoppingItemRepository.save(new ShoppingItem(
                request.description(),
                matchedFoodItem != null ? matchedFoodItem.id() : null,
                suggestedStore));

        return new ToolResult.Success(
                "Added \"%s\" to the shopping list%s.".formatted(item.description(),
                        suggestedStore != null ? " (usually bought at " + suggestedStore + ")" : ""),
                item);
    }
}
