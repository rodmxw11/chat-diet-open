package com.chatdiet.fooditem;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FoodAliasNormalizerTest {

    @Test
    void stripsCombiningMarks() {
        assertThat(FoodAliasNormalizer.normalize("crème")).isEqualTo("creme");
    }

    @Test
    void lowercases() {
        assertThat(FoodAliasNormalizer.normalize("Honey Wheat")).isEqualTo("honey wheat");
    }

    @Test
    void removesApostrophes() {
        assertThat(FoodAliasNormalizer.normalize("Nature's")).isEqualTo("natures");
    }

    @Test
    void expandsAmpersand() {
        assertThat(FoodAliasNormalizer.normalize("mac & cheese")).isEqualTo("mac and cheese");
    }

    @Test
    void turnsHyphensUnderscoresSlashesToSpaces() {
        assertThat(FoodAliasNormalizer.normalize("non-fat")).isEqualTo("non fat");
    }

    @Test
    void stripsRemainingPunctuation() {
        assertThat(FoodAliasNormalizer.normalize("bananas.")).isEqualTo("bananas");
    }

    @Test
    void collapsesWhitespaceAndTrims() {
        assertThat(FoodAliasNormalizer.normalize("  banana   bread  ")).isEqualTo("banana bread");
    }

    @Test
    void dropsLeadingArticleOnly() {
        assertThat(FoodAliasNormalizer.normalize("a banana")).isEqualTo("banana");
        assertThat(FoodAliasNormalizer.normalize("an apple")).isEqualTo("apple");
        assertThat(FoodAliasNormalizer.normalize("the works")).isEqualTo("works");
    }

    @Test
    void hyphenNormalizationRunsBeforeArticleStripping() {
        // "half and half" must survive intact - no leading article, no false positives.
        assertThat(FoodAliasNormalizer.normalize("half and half")).isEqualTo("half and half");
    }

    @Test
    void doesNotStripPreparationWords() {
        // cooked rice differs ~3x per 100g from rice by water content - must stay a distinct alias.
        assertThat(FoodAliasNormalizer.normalize("cooked rice")).isNotEqualTo(FoodAliasNormalizer.normalize("rice"));
    }

    @Test
    void doesNotStripBrandWords() {
        assertThat(FoodAliasNormalizer.normalize("great value smoked ham"))
                .isNotEqualTo(FoodAliasNormalizer.normalize("smoked ham"));
    }

    @Test
    void doesNotRemoveGeneralStopWords() {
        assertThat(FoodAliasNormalizer.normalize("beans with bacon"))
                .isNotEqualTo(FoodAliasNormalizer.normalize("beans"));
    }
}
