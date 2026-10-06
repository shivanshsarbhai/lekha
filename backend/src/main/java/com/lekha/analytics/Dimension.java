package com.lekha.analytics;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * What results can be grouped and filtered by. Each dimension is a fixed SQL fragment over the query's tables
 * ({@code t} transactions, {@code a} allocations, {@code c} category, {@code pc} its parent, {@code acc} account), so
 * a client can combine them freely but never send SQL of its own.
 */
enum Dimension {

	DAY("day", TimeGrain.DAY),
	WEEK("week", TimeGrain.WEEK),
	MONTH("month", TimeGrain.MONTH),
	QUARTER("quarter", TimeGrain.QUARTER),
	YEAR("year", TimeGrain.YEAR),

	/** MON … SUN. */
	DAY_OF_WEEK("day_of_week", null, "upper(to_char(t.transaction_date, 'Dy'))",
			"EXTRACT(ISODOW FROM t.transaction_date)", null),
	/** WEEKDAY or WEEKEND (Saturday and Sunday). */
	DAY_TYPE("day_type", null,
			"CASE WHEN EXTRACT(ISODOW FROM t.transaction_date) >= 6 THEN 'WEEKEND' ELSE 'WEEKDAY' END",
			"CASE WHEN EXTRACT(ISODOW FROM t.transaction_date) >= 6 THEN 'WEEKEND' ELSE 'WEEKDAY' END", null),
	/** JAN … DEC, for comparing the same month across years. */
	MONTH_OF_YEAR("month_of_year", null, "upper(to_char(t.transaction_date, 'Mon'))",
			"EXTRACT(MONTH FROM t.transaction_date)", null),
	/** EARLY (1st–10th), MID (11th–20th) or LATE (21st onwards) in the month. */
	MONTH_PHASE("month_phase", null, """
			CASE WHEN EXTRACT(DAY FROM t.transaction_date) <= 10 THEN 'EARLY'
			     WHEN EXTRACT(DAY FROM t.transaction_date) <= 20 THEN 'MID' ELSE 'LATE' END""",
			"EXTRACT(DAY FROM t.transaction_date)", null),

	KIND("kind", null, "a.kind",
			"CASE a.kind WHEN 'EXPENSE' THEN 1 WHEN 'INCOME' THEN 2 WHEN 'INVESTMENT' THEN 3 WHEN 'LENT' THEN 4 ELSE 5 END",
			null),
	/** The top-level category; sub-categories roll up into it. */
	CATEGORY("category", "COALESCE(pc.id, c.id)", "COALESCE(pc.name, c.name)", "lower(COALESCE(pc.name, c.name))",
			"Uncategorised"),
	/** The exact category, shown as "Parent › Child" for sub-categories. */
	SUBCATEGORY("subcategory", "c.id", "CASE WHEN pc.id IS NULL THEN c.name ELSE pc.name || ' › ' || c.name END",
			"lower(CASE WHEN pc.id IS NULL THEN c.name ELSE pc.name || ' › ' || c.name END)", "Uncategorised"),
	ACCOUNT("account", "acc.id", "acc.nickname", "lower(acc.nickname)", null),
	PAYMENT_MODE("payment_mode", null, "t.payment_mode", "t.payment_mode", "UNKNOWN");

	private final String key;

	/** When set, the dimension returns this id next to its label, and is filtered by it. */
	private final @Nullable String idSql;

	private final String labelSql;

	private final String orderSql;

	/** Shown when the label is null, e.g. an expense saved without a category. */
	private final @Nullable String nullLabel;

	private final @Nullable TimeGrain grain;

	Dimension(String key, TimeGrain grain) {
		this.key = key;
		this.idSql = null;
		this.labelSql = grain.labelSql();
		this.orderSql = grain.labelSql();
		this.nullLabel = null;
		this.grain = grain;
	}

	Dimension(String key, @Nullable String idSql, String labelSql, String orderSql, @Nullable String nullLabel) {
		this.key = key;
		this.idSql = idSql;
		this.labelSql = labelSql;
		this.orderSql = orderSql;
		this.nullLabel = nullLabel;
		this.grain = null;
	}

	String key() {
		return key;
	}

	@Nullable String idSql() {
		return idSql;
	}

	String labelSql() {
		return labelSql;
	}

	String orderSql() {
		return orderSql;
	}

	@Nullable String nullLabel() {
		return nullLabel;
	}

	@Nullable TimeGrain grain() {
		return grain;
	}

	boolean isTime() {
		return grain != null;
	}

	/** Filter values are compared as text: ids for dimensions that have one, labels otherwise. */
	String filterSql() {
		if (idSql != null) {
			return "CAST(" + idSql + " AS text)";
		}
		return nullLabel != null ? "COALESCE(" + labelSql + ", '" + nullLabel + "')" : labelSql;
	}

	static Optional<Dimension> byKey(String key) {
		return Arrays.stream(values()).filter(dimension -> dimension.key.equals(key)).findFirst();
	}

	static String allKeys() {
		return Arrays.stream(values()).map(Dimension::key).collect(Collectors.joining(", "));
	}

}
