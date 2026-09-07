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