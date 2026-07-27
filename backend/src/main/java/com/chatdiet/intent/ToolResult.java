package com.chatdiet.intent;

public sealed interface ToolResult {

    record Success(String message, Object payload) implements ToolResult {
    }

    record NeedsClarification(String question, Object partial) implements ToolResult {
    }

    record NotFound(String what) implements ToolResult {
    }
}
