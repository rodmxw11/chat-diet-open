package com.chatdiet.shopping;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * IntentTool that reverts a purchased shopping list item back to pending (the "uncheck"
 * operation) - a dedicated tool rather than overloading the mark-purchased tool as a toggle, so
 * the model can't flip the wrong direction.
 */
@Component
@IntentTool(
        name = "revert_shopping_item_purchased",
        intents = {"manage_shopping"},
        description = "Revert a shopping list item that was marked purchased back to pending, e.g. if it was marked purchased by mistake."
)
public class RevertShoppingItemTool implements Function<RevertShoppingItemRequest, ToolResult> {

    private final ShoppingItemRepository shoppingItemRepository;

    public RevertShoppingItemTool(ShoppingItemRepository shoppingItemRepository) {
        this.shoppingItemRepository = shoppingItemRepository;
    }

    /**
     * Finds the best-matching purchased shopping item by description and reverts it to pending.
     *
     * @return a {@link ToolResult.NotFound} if no purchased item matches, otherwise a
     *         {@link ToolResult.Success} wrapping the updated {@link ShoppingItem}
     */
    @Override
    public ToolResult apply(RevertShoppingItemRequest request) {
        var existing = shoppingItemRepository.findBestPurchasedMatchByDescription(request.description()).orElse(null);
        if (existing == null) {
            return new ToolResult.NotFound("a purchased shopping item matching \"" + request.description() + "\"");
        }

        var reverted = shoppingItemRepository.save(existing.pending());

        return new ToolResult.Success("Moved \"%s\" back to pending.".formatted(reverted.description()), reverted);
    }
}
