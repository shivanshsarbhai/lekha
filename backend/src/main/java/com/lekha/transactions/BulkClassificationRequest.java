package com.lekha.transactions;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Gives every listed transaction one piece of this kind and category covering its whole amount. Everything is
 * nullable so missing fields get a clear 400.
 */
record BulkClassificationRequest(@Nullable List<@Nullable UUID> transactionIds, @Nullable AllocationKind kind,
		@Nullable UUID categoryId, @Nullable String note) {
}
