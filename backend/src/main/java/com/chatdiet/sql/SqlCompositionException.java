package com.chatdiet.sql;

/**
 * Thrown when {@link SqlComposerService} fails to produce a usable SQL composition from the
 * model's response, or when persisting a newly composed {@link SavedQuery} fails.
 */
public class SqlCompositionException extends RuntimeException {

    public SqlCompositionException(String message) {
        super(message);
    }

    public SqlCompositionException(String message, Throwable cause) {
        super(message, cause);
    }
}
