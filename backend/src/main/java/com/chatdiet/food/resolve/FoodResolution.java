package com.chatdiet.food.resolve;

import com.chatdiet.fooditem.FoodItem;

import java.util.List;

/**
 * The outcome of {@link FoodResolver#resolve}. Separate from {@code ToolResult} deliberately -
 * this is a domain-level resolution with no knowledge of the chat/tool-calling protocol;
 * {@code LogFoodTool} is the boundary that converts a variant here into what the model sees.
 */
public sealed interface FoodResolution {

    /** An exact alias hit resolved this phrase without asking. */
    record Resolved(FoodItem item) implements FoodResolution {
    }

    /**
     * No alias hit, but fuzzy scoring found one clear winner (see the auto-accept thresholds on
     * {@link FoodResolver}). Logged without asking, echoed as auto-matched so a wrong pick is
     * visible immediately, and worth an {@code AUTO}-source alias so the phrase never re-fuzzes.
     */
    record AutoResolved(FoodItem item) implements FoodResolution {
    }

    /**
     * Two or more plausible cached items match and no clear winner cleared the auto-accept bar -
     * the user picks from a numbered list, and the pick is learned as an alias so this phrase
     * never asks again ({@link FoodResolver#learnAlias}).
     */
    record Ambiguous(List<Candidate> candidates) implements FoodResolution {
    }

    /** No alias hit and 0-1 cached candidates - a new phrase. The selection is learned as an alias. */
    record Unknown(List<Candidate> candidates) implements FoodResolution {
    }
}
