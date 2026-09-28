package com.chatdiet.intent;

import com.chatdiet.food.LogFoodRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.time.LocalDateTime;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorReportingToolCallbackTest {

    record StrictRequest(LocalDateTime at) {
    }

    /**
     * The failure mode that crashed a chat request: Spring AI converts the JSON arguments before the
     * tool runs, and a bare date into a LocalDateTime field threw there. Wrapped, the model gets an
     * error it can act on instead.
     */
    @Test
    void anArgumentConversionFailureComesBackAsAnErrorResult() {
        Function<StrictRequest, String> tool = request -> "ran";
        var callback = new ErrorReportingToolCallback(FunctionToolCallback.builder("strict_tool", tool)
                .description("test").inputType(StrictRequest.class).build());

        var result = callback.call("{\"at\": \"2026-09-27\"}");

        assertThat(result).startsWith("Error: the strict_tool call failed and nothing from it was saved")
                .contains("2026-09-27")
                .contains("call strict_tool again");
    }

    @Test
    void aSuccessfulCallPassesThroughUnchanged() {
        Function<StrictRequest, String> tool = request -> "ran at " + request.at();
        var callback = new ErrorReportingToolCallback(FunctionToolCallback.builder("strict_tool", tool)
                .description("test").inputType(StrictRequest.class).build());

        assertThat(callback.call("{\"at\": \"2026-09-27T12:00\"}")).contains("ran at 2026-09-27T12:00");
    }

    /** What the model is told about loggedAt: optional, with the accepted format spelled out. */
    @Test
    void logFoodsLoggedAtIsAnOptionalDescribedString() {
        Function<LogFoodRequest, String> tool = request -> "ok";
        var schema = FunctionToolCallback.builder("log_food", tool).description("test")
                .inputType(LogFoodRequest.class).build().getToolDefinition().inputSchema();

        assertThat(schema).contains("\"loggedAt\"").contains("a date alone (2026-09-27) logs at noon");
        assertThat(schema).doesNotContainPattern("\"required\"\\s*:\\s*\\[[^\\]]*\"loggedAt\"");
    }
}
