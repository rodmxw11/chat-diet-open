package com.chatdiet.barcode;

import com.chatdiet.fdc.FdcClient;
import com.chatdiet.fooditem.FoodAlias;
import com.chatdiet.fooditem.FoodAliasNormalizer;
import com.chatdiet.fooditem.FoodAliasRepository;
import com.chatdiet.fooditem.FoodItem;
import com.chatdiet.fooditem.FoodItemRepository;
import com.chatdiet.openfoodfacts.OpenFoodFactsClient;
import org.springframework.stereotype.Service;

/**
 * Resolves a decoded UPC to a product identity for the scan-to-prefill flow (§6.1/§6.2): a local
 * cache hit needs no network; a miss tries Open Food Facts, then FDC's Branded data type
 * (GTIN-verified, {@link FdcClient#lookupBrandedByUpc}). Either external lookup upserts a
 * {@link FoodItem} and writes a {@link FoodAlias} for its name, so the immediate follow-up
 * {@code log_food} call the prefilled message triggers is guaranteed an exact alias hit - no
 * fuzzy matching on a barcode-identified product. A miss on both routes to manual entry.
 */
@Service
public class UpcResolutionService {

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
            return new UpcResolveResult(cached.get().name(), false, false);
        }

        var off = openFoodFactsClient.lookup(upc);
        if (off.isPresent()) {
            var product = off.get();
            var item = upsertAndAlias(product.name(), upc, product.caloriesPer100g(), product.proteinPer100g(),
                    product.carbsPer100g(), product.fatPer100g(), product.fiberPer100g(), product.sugarPer100g(),
                    product.sodiumMgPer100g(), product.saturatedFatPer100g(), product.cholesterolMgPer100g(),
                    product.potassiumMgPer100g(), product.typicalServingG(), "OFF");
            return new UpcResolveResult(item.name(), false, true);
        }

        var branded = fdcClient.lookupBrandedByUpc(upc);
        if (branded.isPresent()) {
            var product = branded.get().product();
            var item = upsertAndAlias(product.name(), upc, product.caloriesPer100g(), product.proteinPer100g(),
                    product.carbsPer100g(), product.fatPer100g(), product.fiberPer100g(), product.sugarPer100g(),
                    product.sodiumMgPer100g(), product.saturatedFatPer100g(), product.cholesterolMgPer100g(),
                    product.potassiumMgPer100g(), product.typicalServingG(), "FDC");
            return new UpcResolveResult(item.name(), false, true);
        }

        return new UpcResolveResult(null, true, false);
    }

    private FoodItem upsertAndAlias(String name, String upc, Double calories, Double protein, Double carbs,
                                     Double fat, Double fiber, Double sugar, Double sodiumMg, Double saturatedFat,
                                     Double cholesterolMg, Double potassiumMg, Double typicalServingG,
                                     String lookupSource) {
        var item = foodItemRepository.save(new FoodItem(name, upc, calories, protein, carbs, fat, fiber, sugar,
                sodiumMg, saturatedFat, cholesterolMg, potassiumMg, typicalServingG, lookupSource));
        var normalized = FoodAliasNormalizer.normalize(name);
        if (foodAliasRepository.findByAliasNormalized(normalized).isEmpty()) {
            foodAliasRepository.save(new FoodAlias(normalized, item.id(), lookupSource));
        }
        return item;
    }
}
