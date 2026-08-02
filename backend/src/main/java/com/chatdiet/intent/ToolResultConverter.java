package com.chatdiet.intent;

import org.springframework.ai.tool.execution.ToolCallResultConverter;

import java.lang.reflect.Type;

/**
 * Renders a {@link ToolResult} returned by an {@code @IntentTool} function into the plain text
 * string Spring AI sends back to the model as the tool call's result.
 */
class ToolResultConverter implements ToolCallResultConverter {

    /**
     * @param result     must be a {@link ToolResult} instance, cast unchecked
     * @param returnType unused; the conversion is driven entirely by the runtime type of {@code result}
     */
    @Override
    public String convert(Object result, Type returnType) {
        return switch ((ToolResult) result) {
            case ToolResult.Success s -> s.message();
            case ToolResult.NeedsClarification c -> c.question();
            case ToolResult.NotFound n -> "No match for " + n.what();
        };
    }
}
