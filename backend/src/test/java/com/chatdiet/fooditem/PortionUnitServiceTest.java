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
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PortionUnitServiceTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("portion-unit-test.db"));
    }

    @Autowired
    private PortionUnitService portionUnitService;

    @Autowired
    private PortionUnitRepository portionUnitRepository;

    @Autowired
    private FoodItemRepository foodItemRepository;

    private long bananaId;

    @BeforeEach
    void seedBanana() {
        portionUnitRepository.deleteAll();
        foodItemRepository.deleteAll();
        bananaId = foodItemRepository.save(
                new FoodItem("banana", null, 89.0, 1.1, 22.8, 0.3, 2.6, 12.2, 1.0, 0.1, 0.0, 358.0, null, "FDC")).id();
    }

    @Test
    void resolvesAnExactNormalizedMatch() {
        portionUnitRepository.save(new PortionUnit(bananaId, "medium", 118.0, "FDC"));

        assertThat(portionUnitService.resolveGrams(bananaId, "medium")).hasValue(118.0);
    }

    @Test
    void resolvesAnUnambiguousSubstringMatch() {
        // FDC's modifier text includes parenthetical detail the model would never say verbatim.
        portionUnitRepository.save(new PortionUnit(bananaId, "medium (7\" to 7-7/8\" long)", 118.0, "FDC"));

        assertThat(portionUnitService.resolveGrams(bananaId, "medium")).hasValue(118.0);
    }

    @Test
    void refusesToGuessBetweenAmbiguousSubstringMatches() {
        // Real FDC data: "cup, sliced" and "cup, mashed" both contain "cup" but have very
        // different gram weights (150 vs 225) - picking one would be a silent guess.
        portionUnitRepository.save(new PortionUnit(bananaId, "cup, sliced", 150.0, "FDC"));
        portionUnitRepository.save(new PortionUnit(bananaId, "cup, mashed", 225.0, "FDC"));

        assertThat(portionUnitService.resolveGrams(bananaId, "cup")).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoUnitIsKnownAtAll() {
        assertThat(portionUnitService.resolveGrams(bananaId, "handful")).isEmpty();
    }

    @Test
    void learningFromAWeighedEntryDerivesGramsPerUnit() {
        // "2 eggs, about 100g" -> 50g/egg.
        portionUnitService.learnFromWeighedEntry(bananaId, "egg", 2.0, 100.0);

        assertThat(portionUnitService.resolveGrams(bananaId, "egg")).hasValueCloseTo(50.0, within(0.0001));
    }

    @Test
    void learningTheSameUnitAgainUpdatesInPlaceRatherThanDuplicating() {
        portionUnitService.learnFromWeighedEntry(bananaId, "egg", 2.0, 100.0);
        portionUnitService.learnFromWeighedEntry(bananaId, "egg", 1.0, 60.0);

        assertThat(portionUnitRepository.findByFoodItemId(bananaId)).hasSize(1);
        assertThat(portionUnitService.resolveGrams(bananaId, "egg")).hasValueCloseTo(60.0, within(0.0001));
    }
}
