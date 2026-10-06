package com.lekha.analytics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a {@link SemanticQuery} into SQL by joining the fixed fragments of its metrics and dimensions. Every value
 * from the client is a bound parameter, so the SQL text only ever contains fragments written in this package.
 * <p>
 * Result columns: {@code d0_id} (dimensions with ids), {@code d0} label and {@code o0} sort key for each dimension,
 * then {@code m0}, {@code m1}… for each metric.
 */
final class QueryCompiler {

	private QueryCompiler() {
	}

	record CompiledQuery(String sql, Map<String, Object> params) {
	}

	static CompiledQuery compile(SemanticQuery query, int limit) {
		List<String> select = new ArrayList<>();
		List<String> groupBy = new ArrayList<>();
		List<String> orderBy = new ArrayList<>();
		for (int i = 0; i < query.dimensions().size(); i++) {
			Dimension dimension = query.dimensions().get(i);
			if (dimension.idSql() != null) {
				select.add(dimension.idSql() + " AS d" + i + "_id");
				groupBy.add(dimension.idSql());
			}
			select.add(dimension.labelSql() + " AS d" + i);
			groupBy.add(dimension.labelSql());
			select.add("MIN(" + dimension.orderSql() + ") AS o" + i);
			orderBy.add("o" + i + " NULLS LAST");
		}
		for (int i = 0; i < query.metrics().size(); i++) {
			select.add(query.metrics().get(i).sql() + " AS m" + i);
		}

		Map<String, Object> params = new LinkedHashMap<>();
		params.put("from", query.from());
		params.put("to", query.to());
		List<String> where = new ArrayList<>(List.of("t.transaction_date BETWEEN :from AND :to"));
		Set<String> kinds = query.allocationKinds();
		if (!kinds.isEmpty()) {
			where.add("a.kind IN (:kinds)");
			params.put("kinds", List.copyOf(kinds));
		}
		int filterIndex = 0;
		for (Map.Entry<Dimension, List<String>> filter : query.filters().entrySet()) {
			String name = "f" + filterIndex++;
			where.add(filter.getKey().filterSql() + " IN (:" + name + ")");
			params.put(name, filter.getValue());
		}
		params.put("limit", limit);

		StringBuilder sql = new StringBuilder()
			.append("SELECT ").append(String.join(",\n       ", select)).append('\n')
			.append("""
					FROM allocations a
					JOIN transactions t ON t.id = a.transaction_id
					JOIN accounts acc ON acc.id = t.account_id
					LEFT JOIN categories c ON c.id = a.category_id
					LEFT JOIN categories pc ON pc.id = c.parent_id
					""")
			.append("WHERE ").append(String.join("\n  AND ", where)).append('\n');
		if (!groupBy.isEmpty()) {
			sql.append("GROUP BY ").append(String.join(", ", groupBy)).append('\n');
			sql.append("ORDER BY ").append(String.join(", ", orderBy)).append('\n');
		}
		sql.append("LIMIT :limit");
		return new CompiledQuery(sql.toString(), params);
	}

}
