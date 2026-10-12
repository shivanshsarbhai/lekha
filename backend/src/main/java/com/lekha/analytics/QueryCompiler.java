package com.lekha.analytics;

import java.time.LocalDate;
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
 * <p>
 * Every date filter and time dimension uses {@code r.reported_on}: the purchase's date for a refund linked to one, so
 * the refund lowers spending in the month the money was spent.
 * <p>
 * The pieces behind a metric are listed with the same tables and the same conditions as the metric itself, so they
 * always add up to it.
 */
final class QueryCompiler {

	private static final String FROM = """
			FROM allocations a
			JOIN transactions t ON t.id = a.transaction_id
			LEFT JOIN transactions rt ON rt.id = a.refund_of
			CROSS JOIN LATERAL (SELECT COALESCE(rt.transaction_date, t.transaction_date) AS reported_on) r
			JOIN accounts acc ON acc.id = t.account_id
			LEFT JOIN categories c ON c.id = a.category_id
			LEFT JOIN categories pc ON pc.id = c.parent_id
			""";

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
		List<String> where = where(query.allocationKinds(), query.filters(), query.from(), query.to(), params);
		params.put("limit", limit);

		StringBuilder sql = new StringBuilder()
			.append("SELECT ").append(String.join(",\n       ", select)).append('\n')
			.append(FROM)
			.append("WHERE ").append(String.join("\n  AND ", where)).append('\n');
		if (!groupBy.isEmpty()) {
			sql.append("GROUP BY ").append(String.join(", ", groupBy)).append('\n');
			sql.append("ORDER BY ").append(String.join(", ", orderBy)).append('\n');
		}
		sql.append("LIMIT :limit");
		return new CompiledQuery(sql.toString(), params);
	}

	/**
	 * The pieces behind one money metric, biggest contribution first. Columns: the piece, its transaction, the
	 * category label (as the subcategory dimension shows it), {@code contribution}, and {@code piece_count}, how many
	 * pieces the transaction is split into.
	 */
	static CompiledQuery compilePieces(PieceQuery query, int limit) {
		Map<String, Object> params = new LinkedHashMap<>();
		List<String> where = piecesWhere(query, params);
		params.put("limit", limit);
		Metric.Pieces pieces = query.pieces();
		String sql = """
				SELECT a.id, a.transaction_id, t.transaction_date, r.reported_on, t.description, t.amount AS transaction_amount,
				       t.payment_mode, acc.id AS account_id, acc.nickname AS account, a.kind, a.category_id,
				       %s AS category, a.note, a.refund_of, %s AS contribution,
				       (SELECT COUNT(*) FROM allocations x WHERE x.transaction_id = a.transaction_id) AS piece_count
				%sWHERE %s
				ORDER BY contribution DESC, r.reported_on DESC, a.id
				LIMIT :limit""".formatted(Dimension.SUBCATEGORY.labelSql(), pieces.contribution(), FROM,
				String.join("\n  AND ", where));
		return new CompiledQuery(sql, params);
	}

	/** The metric over the same pieces {@link #compilePieces} lists, and how many there are: columns total, pieces. */
	static CompiledQuery compilePieceTotal(PieceQuery query) {
		Map<String, Object> params = new LinkedHashMap<>();
		List<String> where = piecesWhere(query, params);
		String sql = "SELECT %s AS total, COUNT(*) AS pieces\n%sWHERE %s".formatted(query.metric().sql(), FROM,
				String.join("\n  AND ", where));
		return new CompiledQuery(sql, params);
	}

	private static List<String> piecesWhere(PieceQuery query, Map<String, Object> params) {
		List<String> where = where(query.metric().kinds(), query.filters(), query.from(), query.to(), params);
		where.add(query.pieces().condition());
		return where;
	}

	private static List<String> where(Set<String> kinds, Map<Dimension, List<String>> filters, LocalDate from,
			LocalDate to, Map<String, Object> params) {
		params.put("from", from);
		params.put("to", to);
		List<String> where = new ArrayList<>(List.of("r.reported_on BETWEEN :from AND :to"));
		if (!kinds.isEmpty()) {
			where.add("a.kind IN (:kinds)");
			params.put("kinds", List.copyOf(kinds));
		}
		int filterIndex = 0;
		for (Map.Entry<Dimension, List<String>> filter : filters.entrySet()) {
			String name = "f" + filterIndex++;
			where.add(filter.getKey().filterSql() + " IN (:" + name + ")");
			params.put(name, filter.getValue());
		}
		return where;
	}

}
