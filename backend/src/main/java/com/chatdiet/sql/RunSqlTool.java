package com.chatdiet.sql;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@IntentTool(
        name = "run_sql",
        intents = {"run_sql"},
        description = "Answer an analytical question by composing and running read-only SQL against the tracked data - trends, rankings, counts, and any aggregation the other tools don't directly expose. Pass the user's question through as-is; the SQL and result table render inline."
)
public class RunSqlTool implements Function<RunSqlRequest, ToolResult> {

    private final SqlAgentService sqlAgentService;
    private final SqlResultContext sqlResultContext;

    public RunSqlTool(SqlAgentService sqlAgentService, SqlResultContext sqlResultContext) {
        this.sqlAgentService = sqlAgentService;
        this.sqlResultContext = sqlResultContext;
    }

    @Override
    public ToolResult apply(RunSqlRequest request) {
        try {
            var answer = sqlAgentService.answer(request.question());
            sqlResultContext.setAnswer(answer);
            return new ToolResult.Success(
                    "Query returned %d row%s.".formatted(answer.totalRows(), answer.totalRows() == 1 ? "" : "s"),
                    answer);
        } catch (Exception e) {
            return new ToolResult.NeedsClarification(
                    "I couldn't answer that with a query (" + e.getMessage() + "). Could you rephrase it?", null);
        }
    }
}
