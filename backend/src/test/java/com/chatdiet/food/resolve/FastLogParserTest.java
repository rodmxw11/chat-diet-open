package com.chatdiet.food.resolve;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fixtures drawn from the real chat_message history - the grammar exists to catch how entries
 * are actually typed, and to refuse everything that needs the model (backdating, corrections,
 * estimate-style trailing numbers, multi-item sentences, bare clarification picks).
 */
class FastLogParserTest {

    @Test
    void parsesGramsFirstFormsWithOptionalLeadingIAteAndOf() {
        assertThat(FastLogParser.parse("I ate 207 g of greek yogurt"))
                .contains(new FastLogParser.Parsed("207g", "greek yogurt", false));
        assertThat(FastLogParser.parse("142g cheerios"))
                .contains(new FastLogParser.Parsed("142g", "cheerios", false));
        assertThat(FastLogParser.parse("Ate 138 g of Greek yogurt"))
                .contains(new FastLogParser.Parsed("138g", "Greek yogurt", false));
        assertThat(FastLogParser.parse("i ate 85 g tira masu."))
                .contains(new FastLogParser.Parsed("85g", "tira masu", false));
    }

    @Test
    void parsesCalorieFirstForms() {
        assertThat(FastLogParser.parse("I ate 100 cal apple sauce"))
                .contains(new FastLogParser.Parsed("100 cal", "apple sauce", false));
        assertThat(FastLogParser.parse("i ate 250 calories of hot dog buns"))
                .contains(new FastLogParser.Parsed("250 cal", "hot dog buns", false));
        assertThat(FastLogParser.parse("120 kcal of orange juice"))
                .contains(new FastLogParser.Parsed("120 cal", "orange juice", false));
    }

    @Test
    void parsesCountFirstFormsAsBareCounts() {
        assertThat(FastLogParser.parse("2 cheerios"))
                .contains(new FastLogParser.Parsed("2", "cheerios", true));
        assertThat(FastLogParser.parse("I ate 3 slices honey wheat bread"))
                .contains(new FastLogParser.Parsed("3", "slices honey wheat bread", true));
        // "granola" must not be mistaken for a gram marker.
        assertThat(FastLogParser.parse("2 granola bars"))
                .contains(new FastLogParser.Parsed("2", "granola bars", true));
    }

    @Test
    void refusesEverythingThatNeedsTheModel() {
        // Backdating.
        assertThat(FastLogParser.parse("yesterday i ate 100 g of applesauce")).isEmpty();
        // Estimate-style trailing numbers stay with the model's log_food.
        assertThat(FastLogParser.parse("I ate chobani yogurt 140 calories 20 g protein")).isEmpty();
        // No leading number after the strip - articles and bare names go to the model.
        assertThat(FastLogParser.parse("I ate a banana")).isEmpty();
        // Bare clarification-pick digits have no food phrase.
        assertThat(FastLogParser.parse("1")).isEmpty();
        assertThat(FastLogParser.parse("6")).isEmpty();
        // Other intents.
        assertThat(FastLogParser.parse("Weight 180.4")).isEmpty();
        assertThat(FastLogParser.parse("note that queued message has timestamp")).isEmpty();
        assertThat(FastLogParser.parse("")).isEmpty();
        assertThat(FastLogParser.parse(null)).isEmpty();
    }
}