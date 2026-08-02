package com.chatdiet.sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

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
