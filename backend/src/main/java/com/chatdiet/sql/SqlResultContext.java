package com.chatdiet.sql;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Optional;

/** Holds the computed SQL answer for the current chat HTTP request, if run_sql was invoked. */
@Component
@RequestScope
public class SqlResultContext {

    private SqlAnswer answer;

    public void setAnswer(SqlAnswer answer) {
        this.answer = answer;
    }

    public Optional<SqlAnswer> answer() {
        return Optional.ofNullable(answer);
    }
}
