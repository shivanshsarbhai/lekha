package com.lekha.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * The allocation pieces behind a figure. {@code total} is the metric itself and {@code count} how many pieces make it
 * up, even when only the biggest are listed ({@code truncated}).
 */
record PieceList(BigDecimal total, int count, boolean truncated, List<Piece> pieces) {

	/**
	 * One piece and enough of its transaction to recognise it. {@code contribution} is what it adds to the total:
	 * negative for a refund in spending, for example. {@code reportedOn} differs from {@code transactionDate} for a
	 * refund counted in its purchase's month. {@code split} means the transaction has other pieces too.
	 */
	record Piece(UUID id, UUID transactionId, LocalDate transactionDate, LocalDate reportedOn, String description,
			BigDecimal transactionAmount, @Nullable String paymentMode, UUID accountId, String account, String kind,
			@Nullable UUID categoryId, @Nullable String category, @Nullable String note, @Nullable UUID refundOf,
			BigDecimal contribution, boolean split) {
	}

}
