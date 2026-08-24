package com.chatdiet.sql;

/** Thrown when {@link ReadOnlySqlExecutor} fails to execute a composed SQL statement against SQLite. */
public class SqlExecutionException extends RuntimeException {

    public SqlExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
