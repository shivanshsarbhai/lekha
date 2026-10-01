package com.lekha.transactions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Money moving in or out of one account, as reported by the bank. Negative amounts leave the account, positive
 * amounts enter it.
 */
public record Transaction(
		UUID id,
		UUID accountId,
		LocalDate transactionDate,
		@Nullable LocalDate settlementDate,
		String description,
		BigDecimal amount,
		@Nullable BigDecimal balanceAfter,
		@Nullable PaymentMode paymentMode,
		Map<String, String> metadata,
		Instant createdAt) {

	public Transaction {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(accountId, "accountId");
		Objects.requireNonNull(transactionDate, "transactionDate");
		Objects.requireNonNull(description, "description");
		Objects.requireNonNull(amount, "amount");
		Objects.requireNonNull(metadata, "metadata");
		Objects.requireNonNull(createdAt, "createdAt");

		description = description.strip();
		if (description.isEmpty()) {
			throw new IllegalArgumentException("Description must not be blank");
		}

		amount = toMoney(amount);
		if (amount.signum() == 0) {
			throw new IllegalArgumentException("Amount must not be zero");
		}
		if (balanceAfter != null) {
			balanceAfter = toMoney(balanceAfter);
		}

		metadata = Map.copyOf(metadata);
		createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
	}

	/** Creates a transaction that has not been saved yet, with a freshly generated id. */
	public static Transaction create(UUID accountId, LocalDate transactionDate, @Nullable LocalDate settlementDate,
			String description, BigDecimal amount, @Nullable BigDecimal balanceAfter,
			@Nullable PaymentMode paymentMode, Map<String, String> metadata) {
		return new Transaction(UUID.randomUUID(), accountId, transactionDate, settlementDate, description, amount,
				balanceAfter, paymentMode, metadata, Instant.now());
	}

	private static BigDecimal toMoney(BigDecimal value) {
		try {
			return value.setScale(2, RoundingMode.UNNECESSARY);
		}
		catch (ArithmeticException ex) {
			throw new IllegalArgumentException("Money must have at most 2 decimal places: " + value, ex);
		}
	}

}
