package com.chatdiet.intent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Spring bean as a callable tool for the model. The annotated class must be a
 * {@code Function<Req, ToolResult>} where {@code Req} is a simple request record; the JSON
 * schema the model sees is derived from {@code Req} by Spring AI. {@link ToolRegistry} scans all
 * beans carrying this annotation, wraps each in a {@code ToolCallback} keyed by {@link #name()},
 * and {@link PromptAssembler} exposes to the model only the tools whose name is listed under an
 * enabled {@link IntentDefinition}'s {@code toolNames} (matched against {@link #intents()}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface IntentTool {

    /** Unique tool name; must match an entry in some {@link IntentDefinition#toolNames()}. */
    String name();

    /** Description shown to the model to help it decide when/how to call this tool. */
    String description();

    /** Intent names ({@link IntentDefinition#name()}) this tool is logically associated with. */
    String[] intents();
}
