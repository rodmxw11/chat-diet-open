package com.chatdiet.sql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SchemaDdlProviderTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("schema-ddl-test.db"));
    }

    @Autowired
    private SchemaDdlProvider schemaDdlProvider;

    @Test
    void includesApplicationTablesButNotLiquibaseBookkeepingTables() {
        var ddl = schemaDdlProvider.ddl();

        assertThat(ddl).contains("food_entry").contains("weight_entry").contains("saved_query");
        assertThat(ddl).doesNotContainIgnoringCase("DATABASECHANGELOG");
        // Guards against the removed session concept lingering in the schema the SQL agent sees,
        // or the model would happily write queries against a table that no longer exists.
        assertThat(ddl).doesNotContainIgnoringCase("chat_session");
    }

    @Test
    void isCachedAcrossCalls() {
        assertThat(schemaDdlProvider.ddl()).isSameAs(schemaDdlProvider.ddl());
    }
}
