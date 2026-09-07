package com.chatdiet.barcode;

import com.chatdiet.fdc.FdcClient;
import com.chatdiet.fooditem.FoodAlias;
import com.chatdiet.fooditem.FoodAliasRepository;
import com.chatdiet.fooditem.FoodItem;
import com.chatdiet.fooditem.FoodItemRepository;
import com.chatdiet.openfoodfacts.OffProduct;
import com.chatdiet.openfoodfacts.OpenFoodFactsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the upsert semantics of UPC resolution: a rescan updates the existing row (reviving
 * it if soft-deleted) instead of inserting a duplicate, and an alias collision with a different
 * item degrades to a warning, never an error.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UpcResolutionServiceTest {

    private static final String UPC = "016000275270";

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("upc-resolution-test.db"));
    }

    @Autowired
    private UpcResolutionService upcResolutionService;

    @Autowired
    private FoodItemRepository foodItemRepository;

    @Autowired
    private FoodAliasRepository foodAliasRepository;

    @MockitoBean
    private OpenFoodFactsClient openFoodFactsClient;

    @MockitoBean
    private FdcClient fdcClient;

    @BeforeEach
    void clearAll() {
        foodAliasRepository.deleteAll();
        foodItemRepository.deleteAll();
    }

    private static OffProduct cheerios(String name, double calories) {
        return new OffProduct(name, calories, 12.0, 73.0, 6.0, 9.0, 4.0, 500.0, 1.2, 0.0, 600.0, 28.0);
    }

    @Test
    void cacheHitNeedsNoNetworkAndIsNotNew() {
        when(openFoodFactsClient.lookup(UPC)).thenReturn(Optional.of(cheerios("Cheerios", 357.0)));
        assertThat(upcResolutionService.resolve(UPC).wasNew()).isTrue();

        var second = upcResolutionService.resolve(UPC);

        assertThat(second.wasNew()).isFalse();
        assertThat(second.resolvedName()).isEqualTo("Cheerios");
        verify(openFoodFactsClient, times(1)).lookup(UPC);
    }

    @Test
    void rescanOfASoftDeletedItemRevivesAndUpdatesItInPlace() {
        when(openFoodFactsClient.lookup(UPC)).thenReturn(Optional.of(cheerios("Cheerios", 357.0)));
        upcResolutionService.resolve(UPC);
        var original = foodItemRepository.findByUpc(UPC).orElseThrow();
        foodItemRepository.save(original.withDeleted());

        when(openFoodFactsClient.lookup(UPC)).thenReturn(Optional.of(cheerios("Cheerios Reformulated", 340.0)));
        var result = upcResolutionService.resolve(UPC);

        assertThat(result.wasNew()).isTrue();
        assertThat(foodItemRepository.findAllByUpcIncludingDeleted(UPC)).hasSize(1);
        var revived = foodItemRepository.findByUpc(UPC).orElseThrow();
        assertThat(revived.id()).isEqualTo(original.id());
        assertThat(revived.name()).isEqualTo("Cheerios Reformulated");
        assertThat(revived.per100gCalories()).isEqualTo(340.0);
        assertThat(revived.deletedAt()).isNull();
    }

    @Test
    void aliasOwnedByADifferentItemIsLeftInPlaceWithoutFailing() {
        var other = foodItemRepository.save(new FoodItem("Cheerios", null,
                350.0, 11.0, 70.0, 5.0, 8.0, 3.0, 450.0, 1.0, 0.0, 550.0, null, "MANUAL"));
        foodAliasRepository.save(new FoodAlias("cheerios", other.id(), "MANUAL"));
        when(openFoodFactsClient.lookup(UPC)).thenReturn(Optional.of(cheerios("Cheerios", 357.0)));

        var result = upcResolutionService.resolve(UPC);

        assertThat(result.needsManualEntry()).isFalse();
        var alias = foodAliasRepository.findByAliasNormalized("cheerios").orElseThrow();
        assertThat(alias.foodItemId()).isEqualTo(other.id());
        assertThat(foodItemRepository.findByUpc(UPC)).isPresent();
    }

    @Test
    void findOrFetchFallsBackToFdcBrandedWhenOffMisses() {
        when(openFoodFactsClient.lookup(UPC)).thenReturn(Optional.empty());
        when(fdcClient.lookupBrandedByUpc(UPC)).thenReturn(Optional.empty());

        assertThat(upcResolutionService.findOrFetchByUpc(UPC)).isEmpty();
        assertThat(upcResolutionService.resolve(UPC).needsManualEntry()).isTrue();
        verify(fdcClient, times(2)).lookupBrandedByUpc(UPC);
    }
}