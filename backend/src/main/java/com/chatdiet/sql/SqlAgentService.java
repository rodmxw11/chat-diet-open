package com.chatdiet.sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the end-to-end "answer a natural-language question with SQL" flow: composes SQL
 * (reusing a saved query when possible), validates and executes it read-only, persists/bumps the
 * {@link SavedQuery}, caches the full result for CSV export, and returns a display-capped
 * {@link SqlAnswer}.
 */
@Service
public class SqlAgentService {

    private static final int DISPLAY_ROW_CAP = 500;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SqlComposerService sqlComposerService;
    private final SavedQueryRepository savedQueryRepository;
    private final ReadOnlySqlExecutor readOnlySqlExecutor;
    private final SqlResultStore sqlResultStore;

    public SqlAgentService(SqlComposerService sqlComposerService, SavedQueryRepository savedQueryRepository,
                            ReadOnlySqlExecutor readOnlySqlExecutor, SqlResultStore sqlResultStore) {
        this.sqlComposerService = sqlComposerService;
        this.savedQueryRepository = savedQueryRepository;
        this.readOnlySqlExecutor = readOnlySqlExecutor;
        this.sqlResultStore = sqlResultStore;
    }

    /**
     * Composes, validates, and runs SQL to answer {@code question}, then persists the query for
     * reuse and caches the full result for CSV download.
     *
     * @return a display-capped answer (at most {@code DISPLAY_ROW_CAP} rows), with
     *         {@link SqlAnswer#totalRows()} reporting the true match count
     * @throws SqlCompositionException if a valid SQL composition can't be produced
     * @throws IllegalArgumentException if the composed SQL fails {@link SqlValidator}
     * @throws SqlExecutionException if the query fails to execute
     */
    public SqlAnswer answer(String question) {
        var savedQueries = savedQueryRepository.findAllOrderByUseCountDesc();
        var composition = sqlComposerService.compose(question, savedQueries);

        SqlValidator.validate(composition.sql());

        var result = readOnlySqlExecutor.execute(composition.sql(), composition.paramDefs(), composition.params());
        persistSavedQuery(composition);

        var csvId = sqlResultStore.store(result);
        var truncated = result.rows().size() > DISPLAY_ROW_CAP;
        var displayRows = truncated ? result.rows().subList(0, DISPLAY_ROW_CAP) : result.rows();

        return new SqlAnswer(composition.sql(), result.columns(), displayRows, truncated, result.rows().size(),
                csvId);
    }

    private void persistSavedQuery(SqlComposition composition) {
        if (composition.reusedExistingQuery() != null) {
            savedQueryRepository.findByName(composition.reusedExistingQuery())
                    .ifPresent(existing -> savedQueryRepository.save(existing.withUsageBumped()));
            return;
        }

        try {
            var paramDefsJson = OBJECT_MAPPER.writeValueAsString(composition.paramDefs());
            savedQueryRepository.save(
                    new SavedQuery(composition.name(), composition.description(), composition.sql(), paramDefsJson));
        } catch (Exception e) {
            throw new SqlCompositionException("Failed to save the composed query", e);
        }
    }
}
