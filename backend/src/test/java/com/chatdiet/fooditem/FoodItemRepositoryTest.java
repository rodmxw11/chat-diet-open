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

    @BeforeEach
    void clearAll() {
        foodItemRepository.deleteAll();
    }

    @Test
    void aGenericQueryDoesNotMatchAMuchMoreSpecificCachedName() {
        // Deliberately the most-used item, so the old ORDER BY use_count DESC LIMIT 1 behavior
        // would have picked it over no match at all - this is the exact bug: "chicken" scaling
        // by a chicken salad sandwich's per-100g values instead of falling through to FDC/estimate.
        var sandwich = foodItemRepository.save(
                new FoodItem("chicken salad sandwich", null, 250.0, 10.0, 20.0, 15.0, 1.0, 2.0,
                        400.0, 3.0, 30.0, 200.0, null, "MODEL_ESTIMATE"));
        for (int i = 0; i < 20; i++) {
            foodItemRepository.save(sandwich.withUsageBumped());
        }

        assertThat(foodItemRepository.findBestMatchByName("chicken")).isEmpty();
    }

    @Test
    void aOneWordElaborationStillMatches() {
        var item = foodItemRepository.save(
                new FoodItem("chicken breast", null, 165.0, 31.0, 0.0, 3.6, 0.0, 0.0,
                        74.0, 1.0, 85.0, 256.0, null, "FDC"));

        assertThat(foodItemRepository.findBestMatchByName("chicken")).contains(item);
    }

    @Test
    void aWordierQueryStillMatchesAPlainerCachedName() {
        // The case the original bidirectional-substring design was built for: the model's
        // phrasing is wordier than the cached product name, not the other way around.
        var item = foodItemRepository.save(
                new FoodItem("coca-cola", null, 42.0, 0.0, 10.6, 0.0, 0.0, 10.6,
                        2.0, 0.0, 0.0, 0.0, null, "OFF"));

        assertThat(foodItemRepository.findBestMatchByName("Coca-Cola can")).contains(item);
    }
}
