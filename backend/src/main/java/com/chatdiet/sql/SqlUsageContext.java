package com.chatdiet.sql;

import com.chatdiet.chat.TokenUsage;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Optional;

/**
 * Accumulates the SQL-composer subchat's token usage for the current chat HTTP request, so
 * {@code ChatService} can attach it to the persisted chat message. Summed rather than overwritten
 * because a single turn could in principle invoke {@code run_sql} more than once.
 */
@Component
@RequestScope
public class SqlUsageContext {

    private TokenUsage usage;

    public void record(TokenUsage newUsage) {
        usage = usage == null ? newUsage : usage.plus(newUsage);
    }

    public Optional<TokenUsage> usage() {
        return Optional.ofNullable(usage);
    }
}
