package com.chatdiet.fooditem;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * REST surface for the food-items management page - the one form-based CRUD screen in an
 * otherwise chat-driven app, since fixing bad/partial cached nutrition data (a UPC lookup missing
 * sodium, a stale model estimate) had no way to be corrected directly before this.
 */
@RestController
public class FoodItemController {

    private final FoodItemRepository foodItemRepository;

    public FoodItemController(FoodItemRepository foodItemRepository) {
        this.foodItemRepository = foodItemRepository;
    }

    /** Searches cached food items by name substring (blank matches everything). */
    @GetMapping("/api/food-items")
    public List<FoodItem> search(@RequestParam(required = false, defaultValue = "") String q,
                                  @RequestParam(required = false, defaultValue = "false") boolean includeDeleted) {
        return foodItemRepository.search(q, includeDeleted);
    }

    @GetMapping("/api/food-items/{id}")
    public FoodItem byId(@PathVariable Long id) {
        return foodItemRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/api/food-items")
    public FoodItem create(@RequestBody FoodItemUpsertRequest request) {
        return foodItemRepository.save(new FoodItem(request.name(), request.upc(), request.per100gCalories(),
                request.per100gProtein(), request.per100gCarbs(), request.per100gFat(), request.per100gFiber(),
                request.per100gSugar(), request.per100gSodiumMg(), request.per100gSaturatedFat(),
                request.per100gCholesterolMg(), request.per100gPotassiumMg(), request.typicalServingG(),
                request.lookupSource() != null ? request.lookupSource() : "MANUAL"));
    }

    /** Edits an item's nutrition data in place - prospective only; past logged entries snapshot their own totals. */
    @PutMapping("/api/food-items/{id}")
    public FoodItem update(@PathVariable Long id, @RequestBody FoodItemUpsertRequest request) {
        var existing = foodItemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var updated = new FoodItem(existing.id(), request.name(), request.upc(), request.per100gCalories(),
                request.per100gProtein(), request.per100gCarbs(), request.per100gFat(), request.per100gFiber(),
                request.per100gSugar(), request.per100gSodiumMg(), request.per100gSaturatedFat(),
                request.per100gCholesterolMg(), request.per100gPotassiumMg(), request.typicalServingG(),
                request.lookupSource() != null ? request.lookupSource() : existing.lookupSource(),
                existing.useCount(), existing.lastUsedAt(), existing.deletedAt());
        return foodItemRepository.save(updated);
    }

    /** Soft-deletes an item - it stops matching new chat/UPC lookups but stays resolvable for past entries. */
    @DeleteMapping("/api/food-items/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        var existing = foodItemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        foodItemRepository.save(existing.withDeleted());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/food-items/{id}/restore")
    public FoodItem restore(@PathVariable Long id) {
        var existing = foodItemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return foodItemRepository.save(existing.withRestored());
    }
}
