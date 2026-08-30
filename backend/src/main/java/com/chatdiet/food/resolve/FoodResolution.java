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
     * Two or more plausible cached items match - genuinely ambiguous, not a phrase to write an
     * alias for even once the user picks one (it would make the other candidate unreachable by
     * this phrase forever).
     */
    record Ambiguous(List<Candidate> candidates) implements FoodResolution {
    }

    /** No alias hit and 0-1 cached candidates - a new phrase. Writing an alias on selection is safe here. */
    record Unknown(List<Candidate> candidates) implements FoodResolution {
    }
}
