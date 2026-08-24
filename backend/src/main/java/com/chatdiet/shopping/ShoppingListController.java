package com.chatdiet.shopping;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST surface for the dedicated in-store shopping checklist screen, which needs fast
 * pending/purchased toggling without a full chat round-trip.
 */
@RestController
public class ShoppingListController {

    private final ShoppingItemRepository shoppingItemRepository;

    public ShoppingListController(ShoppingItemRepository shoppingItemRepository) {
        this.shoppingItemRepository = shoppingItemRepository;
    }

    /** Returns every shopping item, pending group first, each group chronological. */
    @GetMapping("/api/shopping-items")
    public List<ShoppingItem> list() {
        return shoppingItemRepository.findAllOrderedByStatusThenAddedAt();
    }

    /**
     * Flips an item's status: pending -> purchased or purchased -> pending. Safe as a plain
     * toggle here since the frontend always knows the item's current state before calling this.
     */
    @PostMapping("/api/shopping-items/{id}/toggle")
    public ShoppingItem toggle(@PathVariable Long id) {
        var item = shoppingItemRepository.findById(id).orElseThrow();
        var toggled = ShoppingItem.STATUS_PENDING.equals(item.status()) ? item.purchased(null) : item.pending();
        return shoppingItemRepository.save(toggled);
    }
}
