package com.lekha.analytics;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What can be measured. Each metric is an aggregate over allocations, so it only counts what has been classified and
 * follows the agreed sign rules: spending is expenses net of refunds, transfers count in nothing.
 */
enum Metric {

	SPENDING("spending", "COALESCE(-SUM(a.amount) FILTER (WHERE a.kind = 'EXPENSE'), 0.00)", Type.MONEY,
			Set.of("EXPENSE")),
	INCOME("income", "COALESCE(SUM(a.amount) FILTER (WHERE a.kind = 'INCOME'), 0.00)", Type.MONEY, Set.of("INCOME")),
	INVESTED("invested", "COALESCE(-SUM(a.amount) FILTER (WHERE a.kind = 'INVESTMENT'), 0.00)", Type.MONEY,
			Set.of("INVESTMENT")),
	LENT("lent", "COALESCE(-SUM(a.amount) FILTER (WHERE a.kind = 'LENT'), 0.00)", Type.MONEY, Set.of("LENT")),
	/** (income − spending) ÷ income, to 4 decimals. Null when there is no income to divide by. */
	SAVINGS_RATE("savings_rate", """
			ROUND((SUM(a.amount) FILTER (WHERE a.kind = 'INCOME')
			       + COALESCE(SUM(a.amount) FILTER (WHERE a.kind = 'EXPENSE'), 0))
			      / NULLIF(SUM(a.amount) FILTER (WHERE a.kind = 'INCOME'), 0), 4)""", Type.DERIVED,
			Set.of("EXPENSE", "INCOME")),
	/** Classified transactions. A split transaction counts once per group it appears in. */
	TRANSACTION_COUNT("transaction_count", "COUNT(DISTINCT t.id)", Type.COUNT, Set.of()),
	/**
	 * Transactions with an expense in them, refunds included. It is what {@link #AVERAGE_SPEND} divides by, so spending
	 * is always average_spend × spend_count.
	 */
	SPEND_COUNT("spend_count", "COUNT(DISTINCT t.id) FILTER (WHERE a.kind = 'EXPENSE')", Type.COUNT,
			Set.of("EXPENSE")),
	/** Spending per transaction that has an expense in it. Null when there are none. */
	AVERAGE_SPEND("average_spend", """
			ROUND(-SUM(a.amount) FILTER (WHERE a.kind = 'EXPENSE')
			      / NULLIF(COUNT(DISTINCT t.id) FILTER (WHERE a.kind = 'EXPENSE'), 0), 2)""", Type.DERIVED,
			Set.of("EXPENSE"));

	/** How an empty period is filled: money with 0.00, counts with 0, derived values with null. */
	enum Type {

		MONEY, COUNT, DERIVED

	}

	private final String key;

	private final String sql;

	private final Type type;

	private final Set<String> kinds;

	Metric(String key, String sql, Type type, Set<String> kinds) {
		this.key = key;
		this.sql = sql;
		this.type = type;
		this.kinds = kinds;
	}

	String key() {
		return key;
	}

	String sql() {
		return sql;
	}

	Type type() {
		return type;
	}

	/** The allocation kinds this metric reads. Empty means all of them. */
	Set<String> kinds() {
		return kinds;
	}

	static Optional<Metric> byKey(String key) {
		return Arrays.stream(values()).filter(metric -> metric.key.equals(key)).findFirst();
	}

	static String allKeys() {
		return Arrays.stream(values()).map(Metric::key).collect(Collectors.joining(", "));
	}

}
