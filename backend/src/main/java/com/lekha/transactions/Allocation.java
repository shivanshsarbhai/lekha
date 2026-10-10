package com.lekha.transactions;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * One piece of a transaction's amount, e.g. ₹500 of a ₹2,000 dinner as a Food expense. The pieces of a transaction
 * add up to its amount; that rule spans several allocations, so the service checks it, not this record.
 * <p>
 * A refund is a positive expense. When {@code refundOf} names the purchase it refunds, reports count it in that
 * purchase's month instead of its own.
 */
record Allocation(UUID id, UUID transactionId, AllocationKind kind, @Nullable UUID categoryId, BigDecimal amount,
		@Nullable String note, @Nullable UUID refundOf) {

	static final int MAX_NOTE_LENGTH = 200;

	/** Kinds in declaration order, then larger amounts first: a fixed order, whatever order they were entered in. */
	static final Comparator<Allocation> DISPLAY_ORDER = Comparator.comparing(Allocation::kind)
		.thenComparing(allocation -> allocation.amount().abs(), Comparator.reverseOrder());

	Allocation {
		requireNonNull(id, "id");
		requireNonNull(transactionId, "transactionId");
		requireNonNull(kind, "kind");
		requireNonNull(amount, "amount");

		amount = Money.of(amount);
		if (amount.signum() == 0) {
			throw new IllegalArgumentException("Allocation amount must not be zero");
		}
		if (categoryId != null && !kind.hasCategories()) {
			throw new IllegalArgumentException(kind + " allocations can't have a category");
		}
		if (note != null) {
			note = note.strip();
			if (note.isEmpty()) {
				note = null;
			}
			else if (note.length() > MAX_NOTE_LENGTH) {
				throw new IllegalArgumentException("Note must be at most " + MAX_NOTE_LENGTH + " characters");
			}
		}
		if (refundOf != null) {
			if (kind != AllocationKind.EXPENSE || amount.signum() < 0) {
				throw new IllegalArgumentException("Only money coming back as an expense can be a refund");
			}
			if (refundOf.equals(transactionId)) {
				throw new IllegalArgumentException("A transaction can't be a refund of itself");
			}
		}
	}

	static Allocation create(UUID transactionId, AllocationKind kind, @Nullable UUID categoryId, BigDecimal amount,
			@Nullable String note) {
		return create(transactionId, kind, categoryId, amount, note, null);
	}

	static Allocation create(UUID transactionId, AllocationKind kind, @Nullable UUID categoryId, BigDecimal amount,
			@Nullable String note, @Nullable UUID refundOf) {
		return new Allocation(UUID.randomUUID(), transactionId, kind, categoryId, amount, note, refundOf);
	}

	boolean isRefundLink() {
		return refundOf != null;
	}

	/** A piece of money actually spent: an expense going out, as opposed to a refund coming back. */
	boolean isSpend() {
		return kind == AllocationKind.EXPENSE && amount.signum() < 0;
	}

}
