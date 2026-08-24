package com.chatdiet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BackendApplicationTests {

	@TempDir
	static Path tempDir;

	/**
	 * Without this the context inherits application.yml's datasource, which is the real
	 * ./data/chat-diet file - so merely running the test suite would boot Liquibase against
	 * live personal data and apply pending migrations to it.
	 */
	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("context-load-test.db"));
	}

	@Test
	void contextLoads() {
	}

}
