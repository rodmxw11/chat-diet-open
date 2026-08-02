package com.chatdiet.shopping;

import com.chatdiet.fooditem.FoodItemRepository;
import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@IntentTool(
        name = "add_shopping_item",
        intents = {"manage_shopping"},
        description = "Add an item to the shopping list. Automatically suggests a store based on where this item (or a matching cached food) was bought before."
)
public class AddShoppingItemTool implements Function<AddShoppingItemRequest, ToolResult> {

    private final ShoppingItemRepository shoppingItemRepository;
    private final FoodItemRepository foodItemRepository;
    private final PurchaseHistoryRepository purchaseHistoryRepository;

    public AddShoppingItemTool(ShoppingItemRepository shoppingItemRepository, FoodItemRepository foodItemRepository,
                                PurchaseHistoryRepository purchaseHistoryRepository) {
        this.shoppingItemRepository = shoppingItemRepository;
        this.foodItemRepository = foodItemRepository;
        this.purchaseHistoryRepository = purchaseHistoryRepository;
    }

    @Override
    public ToolResult apply(AddShoppingItemRequest request) {
        var matchedFoodItem = foodItemRepository.findBestMatchByName(request.description()).orElse(null);

        var suggestedStore = matchedFoodItem != null
                ? purchaseHistoryRepository.findMostCommonStoreByFoodItemId(matchedFoodItem.id()).orElse(null)
                : null;
        if (suggestedStore == null) {
            suggestedStore = purchaseHistoryRepository.findMostCommonStoreByDescription(request.description())
                    .orElse(null);
        }

        var item = shoppingItemRepository.save(new ShoppingItem(
                request.description(),
                matchedFoodItem != null ? matchedFoodItem.id() : null,
                suggestedStore,
                request.estimatedCostUsd()));

        return new ToolResult.Success(
                "Added \"%s\" to the shopping list%s.".formatted(item.description(),
                        suggestedStore != null ? " (usually bought at " + suggestedStore + ")" : ""),
                item);
    }
}
