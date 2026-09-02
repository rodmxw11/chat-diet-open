package com.chatdiet.weight;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Records whether a weight entry was actually persisted during the current chat HTTP request.
 * {@code ChatService} uses this to catch the model claiming a weight was logged (a "Logged: ...
 * lbs" - shaped reply) when {@code log_weight} never actually ran - the same real, observed
 * failure mode {@link com.chatdiet.food.FoodLogVerificationContext} guards against for food.
 */
@Component
@RequestScope
public class WeightLogVerificationContext {

    private boolean logged;

    public void markLogged() {
        logged = true;
    }

    public boolean wasLogged() {
        return logged;
    }
}
