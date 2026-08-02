package com.chatdiet.intent;

/**
 * The outcome every {@code @IntentTool} implementation returns from its {@code apply} method.
 * {@link ToolResultConverter} flattens whichever variant is returned into the plain text string
 * that is actually sent back to the model as the tool call's result.
 */
public sealed interface ToolResult {

    /**
     * The tool did what was asked.
     *
     * @param message text shown to the model, e.g. echoing back what was logged
     * @param payload the data produced (e.g. a saved entity or computed series); not sent to the
     *                 model directly, but available to callers that inspect the result object
     *                 before conversion (e.g. request-scoped context beans)
     */
    record Success(String message, Object payload) implements ToolResult {
    }

    /**
     * The tool needs more information from the user before it can act.
     *
     * @param question text shown to the model to relay to the user
     * @param partial  the partially-filled request/data so far, if useful to retain
     */
    record NeedsClarification(String question, Object partial) implements ToolResult {
    }

    /**
     * The tool could not find whatever it was asked to look up or act on.
     *
     * @param what description of the missing thing, used to build the "No match for ..." message
     */
    record NotFound(String what) implements ToolResult {
    }
}
