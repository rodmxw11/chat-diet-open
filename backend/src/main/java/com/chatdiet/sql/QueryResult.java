package com.chatdiet.sql;

import java.util.List;

public record QueryResult(List<String> columns, List<List<Object>> rows) {
}
