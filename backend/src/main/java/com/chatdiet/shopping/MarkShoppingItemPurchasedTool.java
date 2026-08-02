package com.chatdiet.shopping;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * IntentTool that marks a pending shopping list item as purchased and records the purchase in
 * {@link PurchaseHistory}, feeding future store suggestions from {@link AddShoppingItemTool}.
 */
@Component
@IntentTool(
        name = "mark_shopping_item_purchased",
        intents = {"manage_shopping"},
        description = "Mark a pending shopping list item as purchased, recording the store and cost if given. Records the purchase in history so future store suggestions improve."
)
public class MarkShoppingItemPurchasedTool implements Function<MarkShoppingItemPurchasedRequest, ToolResult> {

    private final ShoppingItemRepository shoppingItemRepository;
    private final PurchaseHistoryRepository purchaseHistoryRepository;

    public MarkShoppingItemPurchasedTool(ShoppingItemRepository shoppingItemRepository,
                                          PurchaseHistoryRepository purchaseHistoryRepository) {
        this.shoppingItemRepository = shoppingItemRepository;
        this.purchaseHistoryRepository = purchaseHistoryRepository;
    }

    /**
     * Finds the best-matching pending shopping item by description, marks it purchased, and
     * records the purchase in history.
     *
     * @return a {@link ToolResult.NotFound} if no pending item matches, otherwise a
     *         {@link ToolResult.Success} wrapping the updated {@link ShoppingItem}
     */
    @Override
    public ToolResult apply(MarkShoppingItemPurchasedRequest request) {
        var existing = shoppingItemRepository.findBestPendingMatchByDescription(request.description()).orElse(null);
        if (existing == null) {
            return new ToolResult.NotFound("a pending shopping item matching \"" + request.description() + "\"");
        }

        var purchased = shoppingItemRepository.save(existing.purchased(request.store(), request.costUsd()));

        purchaseHistoryRepository.save(new PurchaseHistory(
                purchased.foodItemId(), purchased.description(), request.store(), purchased.estimatedCostUsd()));

        return new ToolResult.Success(
                "Marked \"%s\" as purchased%s%s.".formatted(purchased.description(),
                        request.store() != null ? " at " + request.store() : "",
                        purchased.estimatedCostUsd() != null
                                ? " for $%.2f".formatted(purchased.estimatedCostUsd()) : ""),
                purchased);
    }
}
