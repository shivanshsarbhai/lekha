package com.lekha.analytics;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * What can be measured. Each metric is an aggregate over allocations, so it only counts what has been classified and
 * follows the agreed sign rules: spending is expenses net of refunds, transfers count in nothing.
 * <p>
 * A money metric can also list the pieces behind it: {@link Pieces#condition} picks the pieces it reads, and each one
 * adds {@link Pieces#contribution} to the total, so the contributions always sum to the metric.
 */
enum Metric {

	SPENDING("spending", "COALESCE(-SUM(a.amount) FILTER (WHERE a.kind = 'EXPENSE'), 0.00)", Type.MONEY,
			Set.of("EXPENSE"), new Pieces("a.kind = 'EXPENSE'", "-a.amount")),
	INCOME("income", "COALESCE(SUM(a.amount) FILTER (WHERE a.kind = 'INCOME'), 0.00)", Type.MONEY, Set.of("INCOME"),
			new Pieces("a.kind = 'INCOME'", "a.amount")),
	/** Net: money put into investments less money that came back from them, e.g. a redemption or a maturity. */
	INVESTED("invested", "COALESCE(-SUM(a.amount) FILTER (WHERE a.kind = 'INVESTMENT'), 0.00)", Type.MONEY,
			Set.of("INVESTMENT"), new Pieces("a.kind = 'INVESTMENT'", "-a.amount")),
	INVESTED_IN("invested_in",
			"COALESCE(-SUM(a.amount) FILTER (WHERE a.kind = 'INVESTMENT' AND a.amount < 0), 0.00)", Type.MONEY,
			Set.of("INVESTMENT"), new Pieces("a.kind = 'INVESTMENT' AND a.amount < 0", "-a.amount")),
	INVESTED_OUT("invested_out",
			"COALESCE(SUM(a.amount) FILTER (WHERE a.kind = 'INVESTMENT' AND a.amount > 0), 0.00)", Type.MONEY,
			Set.of("INVESTMENT"), new Pieces("a.kind = 'INVESTMENT' AND a.amount > 0", "a.amount")),
	/** Net invested ÷ income, to 4 decimals. Null when there is no income to divide by. */
	INVESTMENT_RATE("investment_rate", """
			ROUND(COALESCE(-SUM(a.amount) FILTER (WHERE a.kind = 'INVESTMENT'), 0)
			      / NULLIF(SUM(a.amount) FILTER (WHERE a.kind = 'INCOME'), 0), 4)""", Type.DERIVED,
			Set.of("INVESTMENT", "INCOME"), null),
	LENT("lent", "COALESCE(-SUM(a.amount) FILTER (WHERE a.kind = 'LENT'), 0.00)", Type.MONEY, Set.of("LENT"),
			new Pieces("a.kind = 'LENT'", "-a.amount")),
	/** (income − spending) ÷ income, to 4 decimals. Null when there is no income to divide by. */
	SAVINGS_RATE("savings_rate", """
			ROUND((SUM(a.amount) FILTER (WHERE a.kind = 'INCOME')
			       + COALESCE(SUM(a.amount) FILTER (WHERE a.kind = 'EXPENSE'), 0))
			      / NULLIF(SUM(a.amount) FILTER (WHERE a.kind = 'INCOME'), 0), 4)""", Type.DERIVED,
			Set.of("EXPENSE", "INCOME"), null),
	/** Classified transactions. A split transaction counts once per group it appears in. */
	TRANSACTION_COUNT("transaction_count", "COUNT(DISTINCT t.id)", Type.COUNT, Set.of(), null),
	/**
	 * Transactions that spent money. Refunds aren't counted, since they would make the average look smaller than any
	 * real purchase. It is what {@link #AVERAGE_SPEND} divides by, so spending is always average_spend × spend_count.
	 */
	SPEND_COUNT("spend_count", "COUNT(DISTINCT t.id) FILTER (WHERE a.kind = 'EXPENSE' AND a.amount < 0)", Type.COUNT,
			Set.of("EXPENSE"), null),
	/** Spending, net of refunds, per transaction that spent money. Null when there are none. */
	AVERAGE_SPEND("average_spend", """
			ROUND(-SUM(a.amount) FILTER (WHERE a.kind = 'EXPENSE')
			      / NULLIF(COUNT(DISTINCT t.id) FILTER (WHERE a.kind = 'EXPENSE' AND a.amount < 0), 0), 2)""",
			Type.DERIVED, Set.of("EXPENSE"), null);

	/** How an empty period is filled: money with 0.00, counts with 0, derived values with null. */
	enum Type {

		MONEY, COUNT, DERIVED

	}

	/** Which pieces a money metric reads, and how much each adds to it. */
	record Pieces(String condition, String contribution) {
	}

	private final String key;

	private final String sql;

	private final Type type;

	private final Set<String> kinds;

	private final @Nullable Pieces pieces;

	Metric(String key, String sql, Type type, Set<String> kinds, @Nullable Pieces pieces) {
		this.key = key;
		this.sql = sql;
		this.type = type;
		this.kinds = kinds;
		this.pieces = pieces;
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

	/** Null for rates and counts, which have no list of rupees behind them. */
	@Nullable Pieces pieces() {
		return pieces;
	}

	static Optional<Metric> byKey(String key) {
		return Arrays.stream(values()).filter(metric -> metric.key.equals(key)).findFirst();
	}

	static String allKeys() {
		return Arrays.stream(values()).map(Metric::key).collect(Collectors.joining(", "));
	}

	static String drillableKeys() {
		return Arrays.stream(values())
			.filter(metric -> metric.pieces != null)
			.map(Metric::key)
			.collect(Collectors.joining(", "));
	}

}
