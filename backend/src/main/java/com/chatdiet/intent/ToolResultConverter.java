package com.chatdiet.intent;

import org.springframework.ai.tool.execution.ToolCallResultConverter;

import java.lang.reflect.Type;

class ToolResultConverter implements ToolCallResultConverter {

    @Override
    public String convert(Object result, Type returnType) {
        return switch ((ToolResult) result) {
            case ToolResult.Success s -> s.message();
            case ToolResult.NeedsClarification c -> c.question();
            case ToolResult.NotFound n -> "No match for " + n.what();
        };
    }
}
