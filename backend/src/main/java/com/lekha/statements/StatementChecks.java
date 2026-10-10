package com.lekha.statements;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Parsing and cross-checking shared by the statement parsers, so every bank fails with the same messages. */
final class StatementChecks {

	private StatementChecks() {
	}

	static void check(String field, int expected, int actual) {
		if (expected != actual) {
			throw mismatch(field, String.valueOf(expected), String.valueOf(actual));
		}
	}

	static void check(String field, BigDecimal expected, BigDecimal actual) {
		if (expected.compareTo(actual) != 0) {
			throw mismatch(field, expected.toPlainString(), actual.toPlainString());
		}
	}

	static BigDecimal sum(List<StatementEntry> entries) {
		return entries.stream().map(StatementEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	static LocalDate parseDate(String text, DateTimeFormatter format) {
		try {
			return LocalDate.parse(text, format);
		}
		catch (DateTimeParseException ex) {
			throw new StatementFormatException("Invalid date: " + text);
		}
	}

	/** For amounts printed with Indian digit grouping, such as "1,50,000.00". */
	static BigDecimal parseAmount(String text) {
		return new BigDecimal(text.replace(",", ""));
	}

	private static StatementFormatException mismatch(String field, String expected, String actual) {
		return new StatementFormatException("Statement summary mismatch for " + field + ": the summary says "
				+ expected + " but the transactions give " + actual);
	}

}
