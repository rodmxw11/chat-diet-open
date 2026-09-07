package com.chatdiet.food.resolve;

import com.chatdiet.fooditem.FoodItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FuzzyCandidateGeneratorTest {

    private final FuzzyCandidateGenerator generator = new FuzzyCandidateGenerator();

    private static FoodItem item(String name) {
        return new FoodItem(name, null, 100.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, null, "MANUAL");
    }

    @Test
    void matchesOnCloseEditDistance() {
        var matches = generator.scoreCachedItems("banan", List.of(item("banana")));
        assertThat(matches).extracting(m -> m.item().name()).contains("banana");
    }

    @Test
    void matchesNaivePluralFlip() {
        var matches = generator.scoreCachedItems("oats", List.of(item("oat")));
        assertThat(matches).singleElement().satisfies(m -> {
            assertThat(m.item().name()).isEqualTo("oat");
            assertThat(m.score()).isEqualTo(0.95);
        });
    }

    @Test
    void matchesOnTokenOverlap() {
        var matches = generator.scoreCachedItems("chicken breast grilled", List.of(item("grilled chicken breast")));
        assertThat(matches).extracting(m -> m.item().name()).contains("grilled chicken breast");
    }

    @Test
    void doesNotMatchUnrelatedFoods() {
        var matches = generator.scoreCachedItems("banana", List.of(item("lasagna")));
        assertThat(matches).isEmpty();
    }

    @Test
    void ranksExactMatchFirst() {
        var matches = generator.scoreCachedItems("banana", List.of(item("banana bread"), item("banana")));
        assertThat(matches.get(0).item().name()).isEqualTo("banana");
        assertThat(matches.get(0).score()).isEqualTo(1.0);
    }
}
