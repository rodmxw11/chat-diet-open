package com.chatdiet.shopping;

/**
 * Request for the {@code revert_shopping_item_purchased} tool.
 *
 * @param description text used to find the matching purchased shopping item, not necessarily
 *                    an exact match
 */
public record RevertShoppingItemRequest(String description) {
}
