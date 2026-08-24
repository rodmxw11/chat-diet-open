package com.chatdiet.shopping;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * IntentTool that marks a pending shopping list item as purchased, recording the store if given.
 */
@Component
@IntentTool(
        name = "mark_shopping_item_purchased",
        intents = {"manage_shopping"},
        description = "Mark a pending shopping list item as purchased, recording the store if given."
)
public class MarkShoppingItemPurchasedTool implements Function<MarkShoppingItemPurchasedRequest, ToolResult> {

    private final ShoppingItemRepository shoppingItemRepository;

    public MarkShoppingItemPurchasedTool(ShoppingItemRepository shoppingItemRepository) {
        this.shoppingItemRepository = shoppingItemRepository;
    }

    /**
     * Finds the best-matching pending shopping item by description and marks it purchased.
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

        var purchased = shoppingItemRepository.save(existing.purchased(request.store()));

        return new ToolResult.Success(
                "Marked \"%s\" as purchased%s.".formatted(purchased.description(),
                        request.store() != null ? " at " + request.store() : ""),
                purchased);
    }
}
