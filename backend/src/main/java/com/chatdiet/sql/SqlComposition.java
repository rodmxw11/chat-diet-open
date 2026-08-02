package com.chatdiet.sql;

import java.util.List;

public record SqlComposition(
        String sql,
        List<ParamDef> paramDefs,
        List<Object> params,
        String reusedExistingQuery,
        String name,
        String description
) {
}
