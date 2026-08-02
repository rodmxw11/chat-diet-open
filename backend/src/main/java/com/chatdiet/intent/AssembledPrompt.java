package com.chatdiet.intent;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * The output of {@link PromptAssembler#assemble()}: the fully composed system prompt and the
 * flattened list of tool callbacks for every enabled intent, ready to hand to a
 * {@code ChatClient.Builder}.
 */
public record AssembledPrompt(String systemPrompt, List<ToolCallback> tools) {
}
