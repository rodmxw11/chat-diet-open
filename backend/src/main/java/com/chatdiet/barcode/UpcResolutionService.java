package com.chatdiet.barcode;

import com.chatdiet.fdc.FdcClient;
import com.chatdiet.fooditem.FoodAlias;
import com.chatdiet.fooditem.FoodAliasNormalizer;
import com.chatdiet.fooditem.FoodAliasRepository;
import com.chatdiet.fooditem.FoodItem;
import com.chatdiet.fooditem.FoodItemRepository;
import com.chatdiet.openfoodfacts.OpenFoodFactsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Optional;

/**
 * Resolves a decoded UPC to a product identity for the scan flow (§6.1/§6.2): a local cache hit
 * needs no network; a miss tries Open Food Facts, then FDC's Branded data type (GTIN-verified,
 * {@link FdcClient#lookupBrandedByUpc}). An external hit upserts onto the existing row for that
 * barcode when there is one - a rescan updates nutrition in place (and revives a soft-deleted
 * row, since rescanning is an explicit signal the product is back in use) rather than minting a
 * duplicate - and writes a {@link FoodAlias} for the product name so a name-based mention later
 * resolves deterministically. A miss on both routes to manual entry.
 */
@Service
public class UpcResolutionService {

    private static final Logger log = LoggerFactory.getLogger(UpcResolutionService.class);

    private final FoodItemRepository foodItemRepository;
    private final FoodAliasRepository foodAliasRepository;
    private final OpenFoodFactsClient openFoodFactsClient;
    private final FdcClient fdcClient;

    public UpcResolutionService(FoodItemRepository foodItemRepository, FoodAliasRepository foodAliasRepository,
                                 OpenFoodFactsClient openFoodFactsClient, FdcClient fdcClient) {
        this.foodItemRepository = foodItemRepository;
        this.foodAliasRepository = foodAliasRepository;
        this.openFoodFactsClient = openFoodFactsClient;
        this.fdcClient = fdcClient;
    }

    public UpcResolveResult resolve(String upc) {
        var cached = foodItemRepository.findByUpc(upc);
        if (cached.isPresent()) {
            var item = cached.get();
            return new UpcResolveResult(item.name(), false, false, item.id(), item.typicalServingG());
        }

        var fetched = fetchExternal(upc);
        if (fetched.isPresent()) {
            var item = fetched.get();
            return new UpcResolveResult(item.name(), false, true, item.id(), item.typicalServingG());
        }
        return new UpcResolveResult(null, true, false, null, null);
    }

    /**
     * The cached item for this barcode, fetching (and caching) it from Open Food Facts or FDC
     * Branded when it isn't local yet. Shared with {@code log_food_by_upc} so a typed/spoken UPC
     * gets the same upsert semantics as a scan.
     */
    public Optional<FoodItem> findOrFetchByUpc(String upc) {
        var cached = foodItemRepository.findByUpc(upc);
        if (cached.isPresent()) {
            return cached;
        }
        return fetchExternal(upc);
    }

    private Optional<FoodItem> fetchExternal(String upc) {
        var off = openFoodFactsClient.lookup(upc);
        if (off.isPresent()) {
            var product = off.get();
            return Optional.of(upsertAndAlias(product.name(), upc, product.caloriesPer100g(),
                    product.proteinPer100g(), product.carbsPer100g(), product.fatPer100g(),
                    product.fiberPer100g(), product.sugarPer100g(), product.sodiumMgPer100g(),
                    product.saturatedFatPer100g(), product.cholesterolMgPer100g(),
                    product.potassiumMgPer100g(), product.typicalServingG(), "OFF"));
        }

        var branded = fdcClient.lookupBrandedByUpc(upc);
        if (branded.isPresent()) {
            var product = branded.get().product();
            return Optional.of(upsertAndAlias(product.name(), upc, product.caloriesPer100g(),
                    product.proteinPer100g(), product.carbsPer100g(), product.fatPer100g(),
                    product.fiberPer100g(), product.sugarPer100g(), product.sodiumMgPer100g(),
                    product.saturatedFatPer100g(), product.cholesterolMgPer100g(),
                    product.potassiumMgPer100g(), product.typicalServingG(), "FDC"));
        }
        return Optional.empty();
    }

    private FoodItem upsertAndAlias(String name, String upc, Double calories, Double protein, Double carbs,
                                     Double fat, Double fiber, Double sugar, Double sodiumMg, Double saturatedFat,
                                     Double cholesterolMg, Double potassiumMg, Double typicalServingG,
                                     String lookupSource) {
        var existing = findUpsertTarget(upc);
        FoodItem item;
        if (existing.isPresent()) {
            var prior = existing.get();
            item = foodItemRepository.save(new FoodItem(prior.id(), name, upc, calories, protein, carbs, fat,
                    fiber, sugar, sodiumMg, saturatedFat, cholesterolMg, potassiumMg, typicalServingG,
                    lookupSource, prior.useCount(), prior.lastUsedAt(), null));
        } else {
            item = foodItemRepository.save(new FoodItem(name, upc, calories, protein, carbs, fat, fiber, sugar,
                    sodiumMg, saturatedFat, cholesterolMg, potassiumMg, typicalServingG, lookupSource));
        }

        var normalized = FoodAliasNormalizer.normalize(name);
        var alias = foodAliasRepository.findByAliasNormalized(normalized);
        if (alias.isEmpty()) {
            foodAliasRepository.save(new FoodAlias(normalized, item.id(), lookupSource));
        } else if (!alias.get().foodItemId().equals(item.id())) {
            log.warn("UPC {} product name '{}' normalizes to alias '{}' already owned by food_item {} - "
                            + "name-based mentions will resolve to that item, not food_item {}",
                    upc, name, normalized, alias.get().foodItemId(), item.id());
        }
        return item;
    }

    /** The row a re-fetched barcode should update: the active one if any, else the newest deleted one. */
    private Optional<FoodItem> findUpsertTarget(String upc) {
        var all = foodItemRepository.findAllByUpcIncludingDeleted(upc);
        return all.stream().filter(i -> i.deletedAt() == null).findFirst()
                .or(() -> all.stream().max(Comparator.comparing(FoodItem::id)));
    }
}
