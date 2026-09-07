package com.chatdiet.fooditem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FoodItemRepositoryTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("food-item-repository-test.db"));
    }

    @Autowired
    private FoodItemRepository foodItemRepository;

    @Autowired
    private FoodAliasRepository foodAliasRepository;

    @BeforeEach
    void clearAll() {
        foodAliasRepository.deleteAll();
        foodItemRepository.deleteAll();
    }

    @Test
    void searchMatchesAliasSubstringsAsWellAsNames() {
        var mayo = foodItemRepository.save(new FoodItem("Hellmann's Real Mayonnaise", null,
                680.0, 1.0, 0.0, 75.0, 0.0, 0.0, 600.0, 11.0, 40.0, 20.0, null, "OFF"));
        foodAliasRepository.save(new FoodAlias("helmans mayonaise", mayo.id(), "USER"));
        foodItemRepository.save(new FoodItem("orange juice", null,
                45.0, 0.7, 10.4, 0.2, 0.2, 8.4, 1.0, 0.0, 0.0, 200.0, null, "FDC"));

        // The user's typo alias finds the item even though the name doesn't contain it.
        assertThat(foodItemRepository.search("helmans", false)).extracting(FoodItem::name)
                .containsExactly("Hellmann's Real Mayonnaise");
        // Name matching still works, and a blank query still matches everything.
        assertThat(foodItemRepository.search("juice", false)).extracting(FoodItem::name)
                .containsExactly("orange juice");
        assertThat(foodItemRepository.search("", false)).hasSize(2);
    }

    @Test
    void searchExcludesDeletedItemsEvenOnAnAliasMatch() {
        var mayo = foodItemRepository.save(new FoodItem("Hellmann's Real Mayonnaise", null,
                680.0, 1.0, 0.0, 75.0, 0.0, 0.0, 600.0, 11.0, 40.0, 20.0, null, "OFF"));
        foodAliasRepository.save(new FoodAlias("helmans mayonaise", mayo.id(), "USER"));
        foodItemRepository.save(mayo.withDeleted());

        assertThat(foodItemRepository.search("helmans", false)).isEmpty();
        assertThat(foodItemRepository.search("helmans", true)).hasSize(1);
    }

    @Test
    void findByUpcSeesOnlyActiveItemsButFindAllIncludingDeletedSeesBoth() {
        var item = foodItemRepository.save(new FoodItem("Cheerios", "016000275270",
                357.0, 12.0, 73.0, 6.0, 9.0, 4.0, 500.0, 1.2, 0.0, 600.0, 28.0, "OFF"));
        foodItemRepository.save(item.withDeleted());

        assertThat(foodItemRepository.findByUpc("016000275270")).isEmpty();
        assertThat(foodItemRepository.findAllByUpcIncludingDeleted("016000275270"))
                .singleElement()
                .satisfies(found -> assertThat(found.deletedAt()).isNotNull());
    }
}