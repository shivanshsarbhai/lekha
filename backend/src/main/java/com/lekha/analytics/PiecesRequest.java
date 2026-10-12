package com.lekha.analytics;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Body of POST /api/v1/analytics/pieces: one money metric, with the same filters and dates as a query. Everything is
 * nullable so a missing field gets a clear 400.
 */
record PiecesRequest(@Nullable String metric, @Nullable Map<String, List<String>> filters, @Nullable LocalDate from,
		@Nullable LocalDate to) {
}
