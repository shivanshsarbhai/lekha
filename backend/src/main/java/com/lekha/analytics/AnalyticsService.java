package com.lekha.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import com.lekha.analytics.AnalyticsRepository.DimensionValue;
import com.lekha.analytics.AnalyticsRepository.Row;

@Service
class AnalyticsService {

	static final int MAX_DIMENSIONS = 3;

	static final int MAX_ROWS = 5000;

	static final int MAX_DAYS_BY_DAY = 92;

	static final int MAX_YEARS = 5;

	private final AnalyticsRepository repository;

	AnalyticsService(AnalyticsRepository repository) {
		this.repository = repository;
	}

	QueryResult query(AnalyticsQueryRequest request) {
		SemanticQuery query = validate(request);

		List<Row> rows = repository.run(query, MAX_ROWS + 1);
		List<Row> filled = fillGaps(query, rows);
		if (rows.size() > MAX_ROWS || filled.size() > MAX_ROWS) {
			throw new InvalidQueryException("This query has more than " + MAX_ROWS
					+ " rows. Use fewer dimensions, a coarser time dimension, or a shorter range.");
		}
		filled.sort(AnalyticsService::compareOrder);

		int unclassified = repository.countUnclassified(query.from(), query.to(),
				query.filters().get(Dimension.ACCOUNT));
		return new QueryResult(columns(query), filled.stream().map(row -> shape(query, row)).toList(), unclassified);
	}

	private static SemanticQuery validate(AnalyticsQueryRequest request) {
		List<String> metricKeys = request.metrics() == null ? List.of() : request.metrics();
		if (metricKeys.isEmpty()) {
			throw new InvalidQueryException("Ask for at least one metric: " + Metric.allKeys());
		}
		List<Metric> metrics = new ArrayList<>();
		for (String key : metricKeys) {
			Metric metric = Metric.byKey(key)
				.orElseThrow(() -> new InvalidQueryException(
						"Unknown metric \"" + key + "\". Use one of: " + Metric.allKeys()));
			if (metrics.contains(metric)) {
				throw new InvalidQueryException("Metric \"" + key + "\" is asked for twice");
			}
			metrics.add(metric);
		}

		List<String> dimensionKeys = request.dimensions() == null ? List.of() : request.dimensions();
		if (dimensionKeys.size() > MAX_DIMENSIONS) {
			throw new InvalidQueryException("Group by at most " + MAX_DIMENSIONS + " dimensions");
		}
		List<Dimension> dimensions = new ArrayList<>();
		for (String key : dimensionKeys) {
			Dimension dimension = dimension(key);
			if (dimensions.contains(dimension)) {
				throw new InvalidQueryException("Dimension \"" + key + "\" is asked for twice");
			}
			dimensions.add(dimension);
		}
		if (dimensions.stream().filter(Dimension::isTime).count() > 1) {
			throw new InvalidQueryException("Group by at most one of day, week, month, quarter and year");
		}

		Map<Dimension, List<String>> filters = new LinkedHashMap<>();
		if (request.filters() != null) {
			for (Map.Entry<String, List<String>> filter : request.filters().entrySet()) {
				Dimension dimension = dimension(filter.getKey());
				if (dimension.isTime()) {
					throw new InvalidQueryException(
							"Filter time with \"from\" and \"to\" rather than \"" + filter.getKey() + "\"");
				}
				if (filter.getValue() == null || filter.getValue().isEmpty()) {
					throw new InvalidQueryException("Filter \"" + filter.getKey() + "\" needs at least one value");
				}
				filters.put(dimension, List.copyOf(filter.getValue()));
			}
		}

		LocalDate from = request.from();
		LocalDate to = request.to();
		if (from == null || to == null) {
			throw new InvalidQueryException("\"from\" and \"to\" are both required");
		}
		if (from.isAfter(to)) {
			throw new InvalidQueryException("\"from\" (" + from + ") must not be after \"to\" (" + to + ")");
		}
		if (to.isAfter(from.plusYears(MAX_YEARS).minusDays(1))) {
			throw new InvalidQueryException("The date range must be at most " + MAX_YEARS + " years");
		}
		if (dimensions.contains(Dimension.DAY) && ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS_BY_DAY) {
			throw new InvalidQueryException(
					"Grouping by day allows at most " + MAX_DAYS_BY_DAY + " days. Try week or month.");
		}

		return new SemanticQuery(List.copyOf(metrics), List.copyOf(dimensions), filters, from, to);
	}

	private static Dimension dimension(String key) {
		return Dimension.byKey(key)
			.orElseThrow(() -> new InvalidQueryException(
					"Unknown dimension \"" + key + "\". Use one of: " + Dimension.allKeys()));
	}

	/**
	 * SQL only returns periods that have data. For a time dimension, adds zero rows for the missing periods of every
	 * combination of the other dimensions, so a chart never silently skips a month.
	 */
	private static List<Row> fillGaps(SemanticQuery query, List<Row> rows) {
		List<Row> filled = new ArrayList<>(rows);
		Dimension time = query.timeDimension();
		if (time == null || time.grain() == null) {
			return filled;
		}
		int timeIndex = query.dimensions().indexOf(time);
		List<String> buckets = time.grain().buckets(query.from(), query.to());

		Map<List<DimensionValue>, @Nullable Row> combinations = new LinkedHashMap<>();
		Set<List<DimensionValue>> present = new HashSet<>();
		for (Row row : rows) {
			combinations.putIfAbsent(withoutIndex(row.keys(), timeIndex), row);
			present.add(row.keys());
		}
		if (rows.isEmpty() && query.dimensions().size() == 1) {
			combinations.put(List.of(), null);
		}

		for (Map.Entry<List<DimensionValue>, @Nullable Row> combination : combinations.entrySet()) {
			for (String bucket : buckets) {
				List<DimensionValue> keys = new ArrayList<>(combination.getKey());
				keys.add(timeIndex, new DimensionValue(null, bucket));
				if (present.contains(keys)) {
					continue;
				}
				Row sample = combination.getValue();
				List<@Nullable Object> order = sample == null ? new ArrayList<>() : new ArrayList<>(sample.order());
				if (sample == null) {
					order.add(bucket);
				}
				else {
					order.set(timeIndex, bucket);
				}
				filled.add(new Row(keys, order, zeros(query.metrics())));
			}
		}
		return filled;
	}

	private static List<DimensionValue> withoutIndex(List<DimensionValue> keys, int index) {
		List<DimensionValue> rest = new ArrayList<>(keys);
		rest.remove(index);
		return rest;
	}

	private static List<@Nullable Object> zeros(List<Metric> metrics) {
		List<@Nullable Object> values = new ArrayList<>();
		for (Metric metric : metrics) {
			values.add(switch (metric.type()) {
				case MONEY -> new BigDecimal("0.00");
				case COUNT -> 0L;
				case DERIVED -> null;
			});
		}
		return values;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static int compareOrder(Row left, Row right) {
		for (int i = 0; i < left.order().size(); i++) {
			Comparable a = (Comparable) left.order().get(i);
			Comparable b = (Comparable) right.order().get(i);
			int result = a == null ? (b == null ? 0 : 1) : b == null ? -1 : a.compareTo(b);
			if (result != 0) {
				return result;
			}
		}
		return 0;
	}

	private static List<String> columns(SemanticQuery query) {
		List<String> columns = new ArrayList<>();
		for (Dimension dimension : query.dimensions()) {
			if (dimension.idSql() != null) {
				columns.add(dimension.key() + "_id");
			}
			columns.add(dimension.key());
		}
		query.metrics().forEach(metric -> columns.add(metric.key()));
		return columns;
	}

	private static List<@Nullable Object> shape(SemanticQuery query, Row row) {
		List<@Nullable Object> values = new ArrayList<>();
		for (int i = 0; i < query.dimensions().size(); i++) {
			Dimension dimension = query.dimensions().get(i);
			DimensionValue value = row.keys().get(i);
			if (dimension.idSql() != null) {
				values.add(value.id());
			}
			values.add(value.label() != null ? value.label() : dimension.nullLabel());
		}
		values.addAll(row.metrics());
		return values;
	}

}
