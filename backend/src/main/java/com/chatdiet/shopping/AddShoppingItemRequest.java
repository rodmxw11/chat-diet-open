package com.chatdiet.shopping;

/**
 * Request for the {@code add_shopping_item} tool.
 *
 * @param estimatedCostUsd optional expected cost of the item; may be {@code null}
 */
public record AddShoppingItemRequest(String description, Double estimatedCostUsd) {
}
