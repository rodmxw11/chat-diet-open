package com.chatdiet.intent;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public record AssembledPrompt(String systemPrompt, List<ToolCallback> tools) {
}
