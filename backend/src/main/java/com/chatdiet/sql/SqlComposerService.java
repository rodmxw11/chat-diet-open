package com.chatdiet.sql;

import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Isolated, single-shot subchat on Opus - never sees the main conversation. Given the schema and
 * the existing saved-query library, it either adapts an existing query's SQL to this question's
 * params, or composes a brand new parameterized SELECT.
 *
 * <p>The spec's sequence diagram splits this into a cheaper "match + extract params" step
 * (arguably Haiku-doable) and a separate "compose new SQL" step. This collapses both into one
 * Opus call for simplicity - reuse still avoids inventing new SQL and grows the saved-query
 * library, just without the further cost split.
 */
@Service
public class SqlComposerService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatClient chatClient;
    private final SchemaDdlProvider schemaDdlProvider;

    public SqlComposerService(ChatClient.Builder chatClientBuilder, SchemaDdlProvider schemaDdlProvider) {
        this.schemaDdlProvider = schemaDdlProvider;
        this.chatClient = chatClientBuilder
                .defaultOptions(AnthropicChatOptions.builder().model(Model.CLAUDE_OPUS_4_5))
                .build();
    }

    public SqlComposition compose(String question, List<SavedQuery> savedQueries) {
        var responseText = chatClient.prompt()
                .system(buildSystemPrompt(savedQueries))
                .user(question)
                .call()
                .content();

        return parse(responseText);
    }

    private String buildSystemPrompt(List<SavedQuery> savedQueries) {
        var savedQueryBlock = savedQueries.isEmpty()
                ? "(none yet)"
                : savedQueries.stream()
                        .map(q -> "- %s: %s\n  SQL: %s\n  Params: %s"
                                .formatted(q.name(), q.description(), q.sqlText(), q.paramDefsJson()))
                        .collect(Collectors.joining("\n"));

        return """
                You are a SQL analyst for a personal diet-tracking H2 database. Given the user's
                natural-language question, respond with ONLY a single JSON object - no markdown
                fences, no prose before or after:
                {"sql": string, "paramDefs": [{"name": string, "type": string}], "params": [...],
                 "reusedExistingQuery": string or null, "name": string, "description": string}

                Rules:
                - "sql" must be a single read-only SELECT (or WITH ... SELECT) statement, using
                  "?" placeholders for every literal value - never inline a literal into the SQL.
                - "paramDefs" describes each "?" placeholder in order: a short name, and a type of
                  DATE, DATETIME, STRING, or NUMBER (DATETIME matches this schema's TIMESTAMP
                  columns).
                - "params" gives the actual bound values for THIS question, same order as
                  paramDefs, as native JSON types. DATE/DATETIME values are ISO-8601 strings
                  (e.g. "2026-07-01" or "2026-07-01T00:00:00").
                - If an existing saved query below already answers this shape of question (same
                  structure, different filter values), reuse its exact "sql" verbatim and set
                  "reusedExistingQuery" to its name instead of rephrasing working SQL.
                - Otherwise compose new SQL, set "reusedExistingQuery" to null, and give it a
                  short snake_case "name" plus a one-sentence "description" for the saved-query
                  library.
                - Never write anything but a SELECT - no INSERT/UPDATE/DELETE/DDL, ever.

                Schema:
                %s

                Existing saved queries:
                %s
                """.formatted(schemaDdlProvider.ddl(), savedQueryBlock);
    }

    private SqlComposition parse(String responseText) {
        try {
            var json = stripMarkdownFences(responseText);
            var raw = OBJECT_MAPPER.readValue(json, RawResponse.class);
            return new SqlComposition(
                    raw.sql(),
                    raw.paramDefs() != null ? raw.paramDefs() : List.of(),
                    raw.params() != null ? raw.params() : List.of(),
                    raw.reusedExistingQuery(),
                    raw.name(),
                    raw.description());
        } catch (Exception e) {
            throw new SqlCompositionException("Couldn't compose a query for that question", e);
        }
    }

    private static String stripMarkdownFences(String text) {
        var trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\n", "").replaceFirst("```$", "").trim();
        }
        return trimmed;
    }

    private record RawResponse(
            String sql,
            List<ParamDef> paramDefs,
            List<Object> params,
            String reusedExistingQuery,
            String name,
            String description
    ) {
    }
}
