package com.chatdiet.food;

import com.chatdiet.barcode.UpcResolutionService;
import com.chatdiet.food.resolve.QuantityResolution;
import com.chatdiet.food.resolve.QuantityResolver;
import com.chatdiet.fooditem.FoodItemLogger;
import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * IntentTool that logs a packaged food identified by UPC barcode. Identity comes from
 * {@link UpcResolutionService} - the same local-cache-then-external-lookup upsert the scanner
 * uses, so a typed or spoken UPC can't mint a duplicate of an already-scanned product - then the
 * nutrition is scaled by the amount eaten.
 */
@Component
@IntentTool(
        name = "log_food_by_upc",
        intents = {"log_food"},
        description = "Log a packaged food identified by UPC barcode. Looks up nutrition from Open Food Facts (caching it for reuse) and scales it by the amount eaten. Provide exactly one of quantityG (grams), quantityKcal (calories eaten), or quantityServings (multiples of the product's typical serving)."
)
public class LogFoodByUpcTool implements Function<LogFoodByUpcRequest, ToolResult> {

    private final UpcResolutionService upcResolutionService;
    private final FoodItemLogger foodItemLogger;
    private final QuantityResolver quantityResolver;

    public LogFoodByUpcTool(UpcResolutionService upcResolutionService, FoodItemLogger foodItemLogger,
                             QuantityResolver quantityResolver) {
        this.upcResolutionService = upcResolutionService;
        this.foodItemLogger = foodItemLogger;
        this.quantityResolver = quantityResolver;
    }

    /**
     * Looks up the product by UPC (locally, then via Open Food Facts / FDC Branded, upserting the
     * cached {@code FoodItem}) and logs it scaled to the requested quantity.
     *
     * @return a {@link ToolResult.NotFound} if the UPC matches no known or lookup-able product,
     *         a {@link ToolResult.NeedsClarification} if the quantity could not be resolved, or
     *         the logging result otherwise
     */
    @Override
    public ToolResult apply(LogFoodByUpcRequest request) {
        var item = upcResolutionService.findOrFetchByUpc(request.upc()).orElse(null);
        if (item == null) {
            return new ToolResult.NotFound("a product for UPC " + request.upc());
        }

        var qty = quantityResolver.resolve(item, request.quantityG(), request.quantityServings(),
                request.quantityKcal());
        if (!(qty instanceof QuantityResolution.Grams grams)) {
            return new ToolResult.NeedsClarification(
                    "How many servings (or how many grams) did you have of " + item.name() + "?", item.name());
        }

        return foodItemLogger.logScaled(item, grams.grams(), request.loggedAt());
    }
}
