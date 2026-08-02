package com.chatdiet.sql;

public class SqlCompositionException extends RuntimeException {

    public SqlCompositionException(String message) {
        super(message);
    }

    public SqlCompositionException(String message, Throwable cause) {
        super(message, cause);
    }
}
