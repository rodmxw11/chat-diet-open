package com.chatdiet.intent;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.ApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {

    private static final ToolResultConverter TOOL_RESULT_CONVERTER = new ToolResultConverter();

    private final Map<String, ToolCallback> callbacksByName;

    public ToolRegistry(ApplicationContext context) {
        this.callbacksByName = context.getBeansWithAnnotation(IntentTool.class).values().stream()
                .collect(Collectors.toMap(
                        bean -> bean.getClass().getAnnotation(IntentTool.class).name(),
                        ToolRegistry::buildCallback));
    }

    @SuppressWarnings("unchecked")
    private static ToolCallback buildCallback(Object bean) {
        var meta = bean.getClass().getAnnotation(IntentTool.class);
        Class<?>[] typeArguments = GenericTypeResolver.resolveTypeArguments(bean.getClass(), Function.class);
        Class<?> requestType = typeArguments[0];
        var function = (Function<Object, Object>) bean;
        return FunctionToolCallback.builder(meta.name(), function)
                .description(meta.description())
                .inputType(requestType)
                .toolCallResultConverter(TOOL_RESULT_CONVERTER)
                .build();
    }

    public List<ToolCallback> toolsFor(Collection<String> toolNames) {
        return toolNames.stream()
                .map(callbacksByName::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
