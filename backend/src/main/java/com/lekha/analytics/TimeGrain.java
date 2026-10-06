package com.lekha.analytics;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * A calendar period that time dimensions group by. The SQL label and {@link #label(LocalDate)} must produce the
 * same text, because the service fills in the periods SQL returned nothing for.
 */
enum TimeGrain {

	DAY("to_char(t.transaction_date, 'YYYY-MM-DD')"),
	/** ISO weeks, starting on Monday, labelled by that Monday's date. */
	WEEK("to_char(date_trunc('week', t.transaction_date), 'YYYY-MM-DD')"),
	MONTH("to_char(t.transaction_date, 'YYYY-MM')"),
	QUARTER("to_char(t.transaction_date, 'YYYY-\"Q\"Q')"),
	YEAR("to_char(t.transaction_date, 'YYYY')");

	private final String labelSql;

	TimeGrain(String labelSql) {
		this.labelSql = labelSql;
	}

	String labelSql() {
		return labelSql;
	}

	LocalDate start(LocalDate date) {
		return switch (this) {
			case DAY -> date;
			case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			case MONTH -> date.withDayOfMonth(1);
			case QUARTER -> date.withMonth((date.getMonthValue() - 1) / 3 * 3 + 1).withDayOfMonth(1);
			case YEAR -> date.withDayOfYear(1);
		};
	}

	String label(LocalDate start) {
		return switch (this) {
			case DAY, WEEK -> start.toString();
			case MONTH -> String.format("%d-%02d", start.getYear(), start.getMonthValue());
			case QUARTER -> start.getYear() + "-Q" + ((start.getMonthValue() - 1) / 3 + 1);
			case YEAR -> String.valueOf(start.getYear());
		};
	}

	/** Every period that overlaps {@code from}..{@code to}, oldest first. */
	List<String> buckets(LocalDate from, LocalDate to) {
		List<String> labels = new ArrayList<>();
		for (LocalDate start = start(from); !start.isAfter(to); start = next(start)) {
			labels.add(label(start));
		}
		return labels;
	}

	private LocalDate next(LocalDate start) {
		return switch (this) {
			case DAY -> start.plusDays(1);
			case WEEK -> start.plusWeeks(1);
			case MONTH -> start.plusMonths(1);
			case QUARTER -> start.plusMonths(3);
			case YEAR -> start.plusYears(1);
		};
	}

}
