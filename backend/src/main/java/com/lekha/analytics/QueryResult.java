package com.lekha.analytics;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A table: one column per dimension ({@code category_id} and {@code category} for dimensions with ids), then one per
 * metric, in the order asked for. {@code unclassifiedCount} is how many transactions in the range aren't counted yet
 * because nobody has classified them.
 */
record QueryResult(List<String> columns, List<List<@Nullable Object>> rows, int unclassifiedCount) {
}
