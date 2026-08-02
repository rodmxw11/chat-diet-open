package com.chatdiet.shopping;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@IntentTool(
        name = "list_shopping_items",
        intents = {"manage_shopping"},
        description = "List all pending (not yet purchased) shopping list items, with their suggested store and estimated cost when known."
)
public class ListShoppingItemsTool implements Function<ListShoppingItemsRequest, ToolResult> {

    private final ShoppingItemRepository shoppingItemRepository;

    public ListShoppingItemsTool(ShoppingItemRepository shoppingItemRepository) {
        this.shoppingItemRepository = shoppingItemRepository;
    }

    @Override
    public ToolResult apply(ListShoppingItemsRequest request) {
        var pending = shoppingItemRepository.findPending();
        if (pending.isEmpty()) {
            return new ToolResult.Success("Shopping list is empty.", pending);
        }

        var summary = pending.stream()
                .map(item -> item.description()
                        + (item.suggestedStore() != null ? " (" + item.suggestedStore() + ")" : ""))
                .collect(Collectors.joining(", "));

        return new ToolResult.Success("Shopping list: " + summary + ".", pending);
    }
}
