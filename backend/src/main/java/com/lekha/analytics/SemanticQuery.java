package com.lekha.analytics;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

/** A validated query: known metrics and dimensions, at most one time dimension, and a bounded date range. */
record SemanticQuery(List<Metric> metrics, List<Dimension> dimensions, Map<Dimension, List<String>> filters,
		LocalDate from, LocalDate to) {

	@Nullable Dimension timeDimension() {
		return dimensions.stream().filter(Dimension::isTime).findFirst().orElse(null);
	}

	/** The allocation kinds the metrics read, so rows of other kinds don't appear as empty groups. Empty means all. */
	Set<String> allocationKinds() {
		Set<String> kinds = new TreeSet<>();
		for (Metric metric : metrics) {
			if (metric.kinds().isEmpty()) {
				return Set.of();
			}
			kinds.addAll(metric.kinds());
		}
		return kinds;
	}

}
