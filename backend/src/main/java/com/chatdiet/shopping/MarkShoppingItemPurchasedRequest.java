package com.chatdiet.shopping;

/**
 * Request for the {@code mark_shopping_item_purchased} tool.
 *
 * @param description text used to find the matching pending shopping item, not necessarily
 *                    an exact match
 * @param store       store the purchase was made at, if given; may be {@code null}
 * @param costUsd     actual cost paid, if given; may be {@code null}, in which case the item's
 *                    prior estimated cost is kept
 */
public record MarkShoppingItemPurchasedRequest(String description, String store, Double costUsd) {
}
