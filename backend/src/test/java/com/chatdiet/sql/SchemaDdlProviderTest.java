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
        registry.add("spring.datasource.url", () -> "jdbc:h2:file:" + tempDir.resolve("schema-ddl-test"));
    }

    @Autowired
    private SchemaDdlProvider schemaDdlProvider;

    @Test
    void includesApplicationTablesButNotLiquibaseBookkeepingTables() {
        var ddl = schemaDdlProvider.ddl();

        assertThat(ddl).contains("FOOD_ENTRY").contains("WEIGHT_ENTRY").contains("SAVED_QUERY");
        assertThat(ddl).doesNotContain("DATABASECHANGELOG");
        // Guards the 018 migration: a dropped table must not linger in the schema the SQL agent
        // sees, or the model will happily write queries against a table that no longer exists.
        assertThat(ddl).doesNotContain("CHAT_SESSION");
    }

    @Test
    void isCachedAcrossCalls() {
        assertThat(schemaDdlProvider.ddl()).isSameAs(schemaDdlProvider.ddl());
    }
}
