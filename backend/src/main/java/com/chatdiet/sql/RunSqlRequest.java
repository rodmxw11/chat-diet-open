package com.chatdiet.sql;

/** Request payload for {@link RunSqlTool}: a natural-language analytical question. */
public record RunSqlRequest(String question) {
}
