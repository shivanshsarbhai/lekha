package com.lekha.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

/** JSON body of {@code PUT /api/v1/transactions/{id}}: every field the user may correct, all required. */
record UpdateTransactionRequest(@Nullable LocalDate transactionDate, @Nullable String description,
		@Nullable BigDecimal amount) {
}
