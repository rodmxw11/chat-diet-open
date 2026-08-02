package com.chatdiet.sql;

import java.util.List;

/**
 * Result of {@link SqlComposerService#compose}: either a brand-new parameterized SELECT, or an
 * existing {@link SavedQuery} reused verbatim with new parameter values.
 *
 * @param sql                 the SELECT (or WITH ... SELECT) statement to execute, with
 *                            {@code ?} placeholders
 * @param paramDefs           one entry per {@code ?} placeholder, in order
 * @param params              the bound values for this question, same order as {@code paramDefs}
 * @param reusedExistingQuery name of the {@link SavedQuery} this composition reused, or
 *                            {@code null} if new SQL was composed
 * @param name                short snake_case name to save the query under, when newly composed
 *                            (unused when {@code reusedExistingQuery} is set)
 * @param description         one-sentence description to save alongside a newly composed query
 *                            (unused when {@code reusedExistingQuery} is set)
 */
public record SqlComposition(
        String sql,
        List<ParamDef> paramDefs,
        List<Object> params,
        String reusedExistingQuery,
        String name,
        String description
) {
}
