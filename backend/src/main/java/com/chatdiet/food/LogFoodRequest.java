package com.chatdiet.food;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Request for the {@code log_food} tool: one or more foods from a single utterance, logged
 * together into one {@code entry_group_id}.
 *
 * @param items          the foods to log, in the order mentioned
 * @param attachToGroupId when this call is a clarifying follow-up to a partial batch (some items
 *                        already saved, one or more still unresolved), the group id echoed in the
 *                        prior response - so the follow-up item lands in the same group instead of
 *                        starting a new one. Null to start a new group.
 * @param loggedAt        when this was actually eaten, if the user mentioned a past day and/or
 *                        meal (e.g. "yesterday for dinner"); null to log under the current time.
 *                        Applies to the whole batch, not per item.
 */
public record LogFoodRequest(
        List<LogFoodItemRequest> items,
        Long attachToGroupId,
        LocalDateTime loggedAt
) {
}
