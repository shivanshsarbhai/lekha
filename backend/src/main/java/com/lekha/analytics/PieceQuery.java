package com.lekha.analytics;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A validated request for the pieces behind one money metric. */
record PieceQuery(Metric metric, Map<Dimension, List<String>> filters, LocalDate from, LocalDate to) {

	Metric.Pieces pieces() {
		return Objects.requireNonNull(metric.pieces(), metric.key() + " has no pieces");
	}

}
