package com.chatdiet.shopping;

import com.chatdiet.fooditem.FoodItemRepository;
import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * IntentTool that adds several cached food items to the shopping list at once - the confirm
 * action of the food-item picker round-trips through chat as synthesized natural-language text,
 * which the model then resolves to this tool call.
 */
@Component
@IntentTool(
        name = "add_shopping_items_bulk",
        intents = {"manage_shopping"},
        description = "Add multiple previously logged food items to the shopping list at once, by their food item ids."
)
public class AddShoppingItemsBulkTool implements Function<AddShoppingItemsBulkRequest, ToolResult> {

    private final FoodItemRepository foodItemRepository;
    private final ShoppingItemRepository shoppingItemRepository;

    public AddShoppingItemsBulkTool(FoodItemRepository foodItemRepository, ShoppingItemRepository shoppingItemRepository) {
        this.foodItemRepository = foodItemRepository;
        this.shoppingItemRepository = shoppingItemRepository;
    }

    @Override
    public ToolResult apply(AddShoppingItemsBulkRequest request) {
        var ids = request.foodItemIds() != null ? request.foodItemIds() : List.<Long>of();
        var added = ids.stream()
                .map(id -> foodItemRepository.findById(id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(item -> {
                    var suggestedStore = shoppingItemRepository.findMostCommonStoreByFoodItemId(item.id()).orElse(null);
                    return shoppingItemRepository.save(new ShoppingItem(item.name(), item.id(), suggestedStore));
                })
                .toList();

        var summary = added.stream().map(ShoppingItem::description).collect(Collectors.joining(", "));
        return new ToolResult.Success(
                added.isEmpty() ? "No matching food items found to add." : "Added to the shopping list: " + summary + ".",
                added);
    }
}
