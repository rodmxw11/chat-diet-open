package com.chatdiet.intent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * Turns a tool call that throws into an error result the model can read and act on, instead of an
 * exception that aborts the whole chat request. Matters most for input the model got wrong: Spring
 * AI converts the JSON arguments before the tool runs, so a malformed value (a bare date where a
 * date-time was expected, once) failed there and surfaced to the user as a server error with no
 * reply at all. Now the model sees what was wrong and can call again with fixed arguments.
 */
final class ErrorReportingToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(ErrorReportingToolCallback.class);

    private final ToolCallback delegate;

    ErrorReportingToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        try {
            return toolContext == null ? delegate.call(toolInput) : delegate.call(toolInput, toolContext);
        } catch (RuntimeException e) {
            var name = delegate.getToolDefinition().name();
            log.warn("Tool {} failed - reporting it to the model. Input: {}", name, toolInput, e);
            return "Error: the " + name + " call failed and nothing from it was saved - " + describe(e)
                    + ". Fix the arguments and call " + name + " again; don't tell the user it was saved.";
        }
    }

    /** The innermost cause's message is the useful one - e.g. which value failed to parse. */
    private static String describe(Throwable e) {
        var root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }
}
