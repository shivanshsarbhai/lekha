package com.lekha.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A purchase a refund could be linked to. {@code spent} is its expense total and {@code refundable} what is left of it
 * after other refunds, both positive. A refund must use one of {@code categoryIds}; empty means it was uncategorised.
 */
record RefundCandidate(UUID id, UUID accountId, LocalDate transactionDate, String description, BigDecimal spent,
		BigDecimal refundable, List<UUID> categoryIds) {
}
