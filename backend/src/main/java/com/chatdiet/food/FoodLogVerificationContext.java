package com.chatdiet.food;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Records whether a food entry was actually persisted during the current chat HTTP request.
 * {@code ChatService} uses this to catch the model claiming a food was logged (a "Logged: ...,
 * N kcal" - shaped reply) when no logging tool actually ran - a real, observed failure mode where
 * the model narrates a plausible-sounding confirmation without invoking the function behind it.
 *
 * <p>Deliberately only tracks *whether* a save happened, not which tool - {@code log_food},
 * {@code log_food_by_upc}, and {@code log_cached_food} (via {@link FoodItemLogger}) all mark it the
 * same way, since from the verification layer's perspective they're interchangeable evidence that
 * "yes, something was actually written to food_entry this turn."
 */
@Component
@RequestScope
public class FoodLogVerificationContext {

    private boolean logged;

    public void markLogged() {
        logged = true;
    }

    public boolean wasLogged() {
        return logged;
    }
}
