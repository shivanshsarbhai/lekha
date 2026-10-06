package com.lekha.analytics;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Body of POST /api/v1/analytics/query. Names are the snake_case keys of {@link Metric} and {@link Dimension}; filter
 * values are ids for category, subcategory and account, and labels (e.g. WEEKEND) for the rest. Everything is
 * nullable so a missing field gets a clear 400.
 */
record AnalyticsQueryRequest(@Nullable List<String> metrics, @Nullable List<String> dimensions,
		@Nullable Map<String, List<String>> filters, @Nullable LocalDate from, @Nullable LocalDate to) {
}
