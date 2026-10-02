package com.lekha.transactions;

import java.math.BigDecimal;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** One piece of a classification as the client sends it. Everything is nullable so missing fields get a clear 400. */
record AllocationRequest(@Nullable AllocationKind kind, @Nullable UUID categoryId, @Nullable BigDecimal amount,
		@Nullable String note) {
}
