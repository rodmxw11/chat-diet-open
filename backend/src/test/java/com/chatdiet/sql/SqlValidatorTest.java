package com.chatdiet.sql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlValidatorTest {

    @Test
    void acceptsASimpleSelect() {
        assertThatCode(() -> SqlValidator.validate("SELECT * FROM food_entry WHERE logged_at >= ?"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsAWithClauseSelect() {
        assertThatCode(() -> SqlValidator.validate(
                "WITH totals AS (SELECT total_calories FROM food_entry) SELECT * FROM totals"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsATrailingSemicolon() {
        assertThatCode(() -> SqlValidator.validate("SELECT * FROM food_entry;"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInsert() {
        assertThatThrownBy(() -> SqlValidator.validate("INSERT INTO food_entry (total_calories) VALUES (100)"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDelete() {
        assertThatThrownBy(() -> SqlValidator.validate("DELETE FROM food_entry"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDrop() {
        assertThatThrownBy(() -> SqlValidator.validate("DROP TABLE food_entry"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMultipleStatements() {
        assertThatThrownBy(() -> SqlValidator.validate("SELECT * FROM food_entry; DELETE FROM food_entry"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonSelectStart() {
        assertThatThrownBy(() -> SqlValidator.validate("food_entry SELECT *"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankSql() {
        assertThatThrownBy(() -> SqlValidator.validate("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
