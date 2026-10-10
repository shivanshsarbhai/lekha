package com.lekha.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * A transaction as the list shows it: the bank's facts, plus how it has been classified. An empty list of allocations
 * means it is unclassified. The transaction's fields stay at the top level of the JSON.
 * <p>
 * {@code refundedAmount} is how much later refunds have given back on this purchase, and {@code refundedPurchases}
 * names the purchases this transaction's own refund pieces point at, which may be in another month. {@code suggestion}
 * is the AI's idea for an unclassified transaction, if it has looked at it.
 */
record ListedTransaction(@JsonUnwrapped Transaction transaction, List<Allocation> allocations,
		BigDecimal refundedAmount, List<RefundedPurchase> refundedPurchases, @Nullable AiSuggestion suggestion) {

	record RefundedPurchase(UUID id, LocalDate transactionDate, String description) {

		static RefundedPurchase of(Transaction purchase) {
			return new RefundedPurchase(purchase.id(), purchase.transactionDate(), purchase.description());
		}

	}

	ListedTransaction(Transaction transaction, List<Allocation> allocations) {
		this(transaction, allocations, new BigDecimal("0.00"), List.of(), null);
	}

	boolean isClassified() {
		return !allocations.isEmpty();
	}

}
