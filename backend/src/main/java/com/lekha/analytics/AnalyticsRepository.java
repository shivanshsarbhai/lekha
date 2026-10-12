package com.lekha.analytics;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Read-only queries over the transactions, allocations, categories and accounts tables. Never writes. */
@Repository
class AnalyticsRepository {

	/** One group's dimension value: an id for dimensions that have one, and the label shown. */
	record DimensionValue(@Nullable Object id, @Nullable String label) {
	}

	/** One result row before it is shaped for the response: a value and a sort key per dimension, then the metrics. */
	record Row(List<DimensionValue> keys, List<@Nullable Object> order, List<@Nullable Object> metrics) {
	}

	private final JdbcClient jdbc;

	AnalyticsRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	List<Row> run(SemanticQuery query, int limit) {
		QueryCompiler.CompiledQuery compiled = QueryCompiler.compile(query, limit);
		return jdbc.sql(compiled.sql()).params(compiled.params()).query((rs, rowNum) -> mapRow(rs, query)).list();
	}

	List<PieceList.Piece> pieces(PieceQuery query, int limit) {
		QueryCompiler.CompiledQuery compiled = QueryCompiler.compilePieces(query, limit);
		return jdbc.sql(compiled.sql()).params(compiled.params()).query((rs, rowNum) -> new PieceList.Piece(
				rs.getObject("id", UUID.class), rs.getObject("transaction_id", UUID.class),
				rs.getObject("transaction_date", LocalDate.class), rs.getObject("reported_on", LocalDate.class),
				rs.getString("description"), rs.getBigDecimal("transaction_amount"), rs.getString("payment_mode"),
				rs.getObject("account_id", UUID.class), rs.getString("account"), rs.getString("kind"),
				rs.getObject("category_id", UUID.class), rs.getString("category"), rs.getString("note"),
				rs.getObject("refund_of", UUID.class), rs.getBigDecimal("contribution"), rs.getInt("piece_count") > 1))
			.list();
	}

	/** The metric over every matching piece, and how many there are. */
	Map.Entry<BigDecimal, Integer> pieceTotal(PieceQuery query) {
		QueryCompiler.CompiledQuery compiled = QueryCompiler.compilePieceTotal(query);
		return jdbc.sql(compiled.sql())
			.params(compiled.params())
			.query((rs, rowNum) -> Map.entry(rs.getBigDecimal("total"), rs.getInt("pieces")))
			.single();
	}

	/** Transactions in the range with no allocations at all, optionally only in some accounts. */
	int countUnclassified(LocalDate from, LocalDate to, @Nullable List<String> accountIds) {
		Long count = jdbc.sql("""
				SELECT COUNT(*)
				FROM transactions t
				WHERE t.transaction_date BETWEEN :from AND :to
				  AND (:allAccounts OR CAST(t.account_id AS text) IN (:accountIds))
				  AND NOT EXISTS (SELECT 1 FROM allocations a WHERE a.transaction_id = t.id)
				""")
			.param("from", from)
			.param("to", to)
			.param("allAccounts", accountIds == null)
			.param("accountIds", accountIds == null ? List.of("") : accountIds)
			.query(Long.class)
			.single();
		return count.intValue();
	}

	private static Row mapRow(ResultSet rs, SemanticQuery query) throws SQLException {
		List<DimensionValue> keys = new ArrayList<>();
		List<@Nullable Object> order = new ArrayList<>();
		for (int i = 0; i < query.dimensions().size(); i++) {
			Dimension dimension = query.dimensions().get(i);
			Object id = dimension.idSql() != null ? rs.getObject("d" + i + "_id", UUID.class) : null;
			keys.add(new DimensionValue(id, rs.getString("d" + i)));
			order.add(rs.getObject("o" + i));
		}
		List<@Nullable Object> metrics = new ArrayList<>();
		for (int i = 0; i < query.metrics().size(); i++) {
			metrics.add(query.metrics().get(i).type() == Metric.Type.COUNT ? (Object) rs.getLong("m" + i)
					: rs.getBigDecimal("m" + i));
		}
		return new Row(keys, order, metrics);
	}

}
