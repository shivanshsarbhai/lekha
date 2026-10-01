package com.lekha.statements;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.lekha.transactions.PaymentMode;

/**
 * One transaction read from a statement, in the same form for every bank. Negative amounts left the account,
 * positive amounts entered it.
 */
record StatementEntry(
		LocalDate transactionDate,
		@Nullable LocalDate settlementDate,
		String description,
		BigDecimal amount,
		@Nullable BigDecimal balanceAfter,
		@Nullable PaymentMode paymentMode,
		Map<String, String> metadata) {

	StatementEntry {
		Objects.requireNonNull(transactionDate, "transactionDate");
		Objects.requireNonNull(description, "description");
		Objects.requireNonNull(amount, "amount");
		metadata = Map.copyOf(metadata);
	}

}
