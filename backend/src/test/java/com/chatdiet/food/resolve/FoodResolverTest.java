package com.chatdiet.food.resolve;

import com.chatdiet.fdc.FdcCandidate;
import com.chatdiet.fdc.FdcClient;
import com.chatdiet.fooditem.FoodAlias;
import com.chatdiet.fooditem.FoodAliasRepository;
import com.chatdiet.fooditem.FoodItem;
import com.chatdiet.fooditem.FoodItemRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Exercises the full §4.2 resolution state diagram: alias hit/miss, ambiguous vs. unknown, stale alias. */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FoodResolverTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("food-resolver-test.db"));
    }

    @Autowired
    private FoodResolver foodResolver;

    @Autowired
    private FoodAliasRepository foodAliasRepository;

    @Autowired
    private FoodItemRepository foodItemRepository;

    @MockitoBean
    private FdcClient fdcClient;

    @BeforeEach
    void clearAll() {
        foodAliasRepository.deleteAll();
        foodItemRepository.deleteAll();
    }

    @Test
    void exactAliasHitResolvesWithoutTouchingFdc() {
        var item = foodItemRepository.save(new FoodItem("Honey Wheat Bread", null,
                250.0, 8.0, 45.0, 3.0, 2.0, 5.0, 300.0, 0.5, 0.0, 100.0, null, "MANUAL"));
        foodAliasRepository.save(new FoodAlias("honey wheat bread", item.id(), "USER"));

        var resolution = foodResolver.resolve("Honey Wheat Bread");

        assertThat(resolution).isInstanceOf(FoodResolution.Resolved.class);
        assertThat(((FoodResolution.Resolved) resolution).item().id()).isEqualTo(item.id());
    }

    @Test
    void aliasMissWithTwoOrMoreCachedCandidatesIsAmbiguousAndDoesNotConsultFdc() {
        foodItemRepository.save(new FoodItem("Honey Wheat Bread", null,
                250.0, 8.0, 45.0, 3.0, 2.0, 5.0, 300.0, 0.5, 0.0, 100.0, null, "MANUAL"));
        foodItemRepository.save(new FoodItem("Whole Wheat Bread", null,
                240.0, 9.0, 44.0, 3.5, 3.0, 4.0, 280.0, 0.6, 0.0, 100.0, null, "MANUAL"));

        var resolution = foodResolver.resolve("wheat bread");

        assertThat(resolution).isInstanceOf(FoodResolution.Ambiguous.class);
        assertThat(((FoodResolution.Ambiguous) resolution).candidates()).hasSize(2);
    }

    @Test
    void aliasMissWithZeroOrOneCachedCandidateIsUnknownAndIncludesFdcCandidates() {
        when(fdcClient.search("celery sticks", 5)).thenReturn(List.of(
                new FdcCandidate("Celery, raw", 1001L),
                new FdcCandidate("Celery, cooked", 1002L)));

        var resolution = foodResolver.resolve("celery sticks");

        assertThat(resolution).isInstanceOf(FoodResolution.Unknown.class);
        var candidates = ((FoodResolution.Unknown) resolution).candidates();
        assertThat(candidates).extracting(Candidate::fdcId).contains(1001L, 1002L);
        assertThat(candidates).anyMatch(Candidate::isEstimateOption);
    }

    @Test
    void aliasBehindASoftDeletedItemFallsThroughToCandidateGeneration() {
        var item = foodItemRepository.save(new FoodItem("orange juice", null,
                45.0, 0.7, 10.4, 0.2, 0.2, 8.4, 1.0, 0.0, 0.0, 200.0, null, "FDC"));
        foodAliasRepository.save(new FoodAlias("orange juice", item.id(), "MANUAL"));
        foodItemRepository.save(item.withDeleted());
        when(fdcClient.search("orange juice", 5)).thenReturn(List.of());

        var resolution = foodResolver.resolve("orange juice");

        assertThat(resolution).isInstanceOf(FoodResolution.Unknown.class);
    }

    @Test
    void confirmSelectionWritesAnAliasOnlyForAnUnknownResolution() {
        var item = foodItemRepository.save(new FoodItem("celery", null,
                16.0, 0.7, 3.0, 0.2, 1.6, 1.3, 80.0, 0.0, 0.0, 260.0, null, "FDC"));

        foodResolver.confirmSelection("celery sticks", item.id(), false);
        assertThat(foodAliasRepository.findByAliasNormalized("celery sticks")).isPresent();

        foodResolver.confirmSelection("stalk of celery", item.id(), true);
        assertThat(foodAliasRepository.findByAliasNormalized("stalk of celery")).isEmpty();
    }
}
