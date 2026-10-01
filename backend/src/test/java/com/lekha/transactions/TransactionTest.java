package com.lekha.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TransactionTest {

	private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@Test
	void amountIsStoredWithExactlyTwoDecimals() {
		Transaction transaction = transactionWith("UPI-SWIGGY", new BigDecimal("-450"), Map.of());

		assertThat(transaction.amount()).isEqualTo(new BigDecimal("-450.00"));
	}

	@Test
	void amountWithMoreThanTwoDecimalsIsRejectedInsteadOfRounded() {
		assertThatThrownBy(() -> transactionWith("UPI-SWIGGY", new BigDecimal("-450.005"), Map.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Money must have at most 2 decimal places: -450.005");
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "0.00", "-0.00" })
	void zeroAmountIsRejected(String zero) {
		assertThatThrownBy(() -> transactionWith("UPI-SWIGGY", new BigDecimal(zero), Map.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Amount must not be zero");
	}

	@Test
	void blankDescriptionIsRejected() {
		assertThatThrownBy(() -> transactionWith("   ", new BigDecimal("-450.00"), Map.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Description must not be blank");
	}

	@Test
	void metadataCannotBeChangedAfterCreation() {
		Map<String, String> metadata = new HashMap<>();
		metadata.put("upiId", "swiggy@icici");
		Transaction transaction = transactionWith("UPI-SWIGGY", new BigDecimal("-450.00"), metadata);

		metadata.put("upiId", "changed@later");

		assertThat(transaction.metadata()).containsEntry("upiId", "swiggy@icici");
		assertThatThrownBy(() -> transaction.metadata().put("x", "y"))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	private static Transaction transactionWith(String description, BigDecimal amount, Map<String, String> metadata) {
		return Transaction.create(ACCOUNT_ID, LocalDate.of(2026, 9, 1), null, description, amount, null, null,
				metadata);
	}

}
