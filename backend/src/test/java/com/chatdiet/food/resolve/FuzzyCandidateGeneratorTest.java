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
        var matches = generator.matchCachedItems("banan", List.of(item("banana")));
        assertThat(matches).extracting(FoodItem::name).contains("banana");
    }

    @Test
    void matchesNaivePluralFlip() {
        var matches = generator.matchCachedItems("oats", List.of(item("oat")));
        assertThat(matches).extracting(FoodItem::name).contains("oat");
    }

    @Test
    void matchesOnTokenOverlap() {
        var matches = generator.matchCachedItems("chicken breast grilled", List.of(item("grilled chicken breast")));
        assertThat(matches).extracting(FoodItem::name).contains("grilled chicken breast");
    }

    @Test
    void doesNotMatchUnrelatedFoods() {
        var matches = generator.matchCachedItems("banana", List.of(item("lasagna")));
        assertThat(matches).isEmpty();
    }

    @Test
    void ranksExactMatchFirst() {
        var matches = generator.matchCachedItems("banana", List.of(item("banana bread"), item("banana")));
        assertThat(matches.get(0).name()).isEqualTo("banana");
    }
}
