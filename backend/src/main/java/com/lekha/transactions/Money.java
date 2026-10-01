package com.lekha.transactions;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Rupee amounts are stored as NUMERIC(14, 2), so every amount is normalised to exactly 2 decimal places. */
final class Money {

	private Money() {
	}

	/** Rejects rather than rounds: an amount with more than 2 decimals means the input was wrong. */
	static BigDecimal of(BigDecimal value) {
		try {
			return value.setScale(2, RoundingMode.UNNECESSARY);
		}
		catch (ArithmeticException ex) {
			throw new IllegalArgumentException("Money must have at most 2 decimal places: " + value, ex);
		}
	}

}
