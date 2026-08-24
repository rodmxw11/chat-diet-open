package com.chatdiet.shopping;

import java.util.List;

/** Request for the {@code add_shopping_items_bulk} tool. */
public record AddShoppingItemsBulkRequest(List<Long> foodItemIds) {
}
