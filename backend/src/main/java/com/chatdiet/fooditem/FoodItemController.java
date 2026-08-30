package com.chatdiet.fooditem;

import com.chatdiet.fdc.FdcClient;
import com.chatdiet.fdc.FdcProduct;
import com.chatdiet.openfoodfacts.OffProduct;
import com.chatdiet.openfoodfacts.OpenFoodFactsClient;
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
    private final FdcClient fdcClient;
    private final OpenFoodFactsClient openFoodFactsClient;

    public FoodItemController(FoodItemRepository foodItemRepository, FdcClient fdcClient,
                               OpenFoodFactsClient openFoodFactsClient) {
        this.foodItemRepository = foodItemRepository;
        this.fdcClient = fdcClient;
        this.openFoodFactsClient = openFoodFactsClient;
    }

    /** Searches cached food items by name substring (blank matches everything). */
    @GetMapping("/api/food-items")
    public List<FoodItem> search(@RequestParam(required = false, defaultValue = "") String q,
                                  @RequestParam(required = false, defaultValue = "false") boolean includeDeleted) {
        return foodItemRepository.search(q, includeDeleted);
    }

    /**
     * Looks up a name in USDA FoodData Central to help fill out the form - returns up to 5
     * plausible candidates for the caller to choose from; empty list if no key is configured or
     * nothing matched. Not cached as a FoodItem itself - the form decides whether to save
     * whichever candidate is picked.
     */
    @GetMapping("/api/food-items/lookup")
    public List<FdcProduct> lookup(@RequestParam String q) {
        return fdcClient.searchCandidates(q, 5);
    }

    /**
     * Looks up a barcode against Open Food Facts to help fill out the form for a packaged product
     * without needing to scan it. Unlike the FDC name search, a barcode lookup is exact, so there's
     * only ever one possible result - {@code null} (still a 200) if the barcode is unknown, same as
     * the FDC lookup returning an empty list rather than a 404 for "nothing found".
     */
    @GetMapping("/api/food-items/lookup-upc")
    public OffProduct lookupByUpc(@RequestParam String upc) {
        return openFoodFactsClient.lookup(upc).orElse(null);
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
