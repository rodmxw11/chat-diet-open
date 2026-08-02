package com.chatdiet.sql;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Bounded in-memory cache of full (uncapped) query results, keyed for CSV download. */
@Component
public class SqlResultStore {

    private static final int MAX_ENTRIES = 20;

    private final Map<String, QueryResult> resultsById = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, QueryResult> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });

    public String store(QueryResult result) {
        var id = UUID.randomUUID().toString();
        resultsById.put(id, result);
        return id;
    }

    public Optional<QueryResult> get(String id) {
        return Optional.ofNullable(resultsById.get(id));
    }
}
