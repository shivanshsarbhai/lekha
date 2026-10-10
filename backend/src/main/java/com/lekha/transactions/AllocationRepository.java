package com.lekha.transactions;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AllocationRepository {

	/**
	 * The date a piece is reported on: the purchase's date for a linked refund, the transaction's own date otherwise.
	 * Needs {@code a} (allocations), {@code t} (its transaction) and {@code rt} (the refunded purchase, left-joined).
	 */
	static final String REPORTED_ON = "COALESCE(rt.transaction_date, t.transaction_date)";

	/** A refund linked to a purchase, with enough of the refunding transaction to name it in a message. */
	record RefundLink(UUID transactionId, String description, @Nullable UUID categoryId, BigDecimal amount) {
	}

	private final JdbcClient jdbc;

	AllocationRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * The allocations of many transactions in one query, grouped by transaction and in {@link Allocation#DISPLAY_ORDER}.
	 * Transactions with no allocations are absent from the map.
	 */
	Map<UUID, List<Allocation>> findByTransactionIds(Collection<UUID> transactionIds) {
		if (transactionIds.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
				SELECT id, transaction_id, kind, category_id, amount, note, refund_of
				FROM allocations
				WHERE transaction_id IN (:transactionIds)
				""")
				.param("transactionIds", List.copyOf(transactionIds))
				.query(AllocationRepository::mapRow)
				.list()
				.stream()
				.sorted(Allocation.DISPLAY_ORDER)
				.collect(Collectors.groupingBy(Allocation::transactionId));
	}

	/**
	 * Swaps a transaction's allocations for a new set, and drops the AI's suggestion for it: once you've decided, it
	 * has served its purpose. Call inside a database transaction so it is all-or-nothing.
	 */
	void replace(UUID transactionId, List<Allocation> allocations) {
		for (Allocation allocation : allocations) {
			if (!allocation.transactionId().equals(transactionId)) {
				throw new IllegalArgumentException("Allocation " + allocation.id() + " belongs to another transaction");
			}
		}

		jdbc.sql("DELETE FROM ai_suggestions WHERE transaction_id = :transactionId")
				.param("transactionId", transactionId)
				.update();
		jdbc.sql("DELETE FROM allocations WHERE transaction_id = :transactionId")
				.param("transactionId", transactionId)
				.update();
		for (Allocation allocation : allocations) {
			jdbc.sql("""
					INSERT INTO allocations (id, transaction_id, kind, category_id, amount, note, refund_of)
					VALUES (:id, :transactionId, :kind, :categoryId, :amount, :note, :refundOf)
					""")
					.param("id", allocation.id())
					.param("transactionId", allocation.transactionId())
					.param("kind", allocation.kind().name())
					.param("categoryId", allocation.categoryId())
					.param("amount", allocation.amount())
					.param("note", allocation.note())
					.param("refundOf", allocation.refundOf())
					.update();
		}
	}

	/** Every refund linked to the purchase, from any transaction. */
	List<RefundLink> refundsOf(UUID purchaseId) {
		return jdbc.sql("""
				SELECT a.transaction_id, t.description, a.category_id, a.amount
				FROM allocations a
				JOIN transactions t ON t.id = a.transaction_id
				WHERE a.refund_of = :purchaseId
				ORDER BY t.transaction_date, t.id
				""")
				.param("purchaseId", purchaseId)
				.query((rs, rowNum) -> new RefundLink(rs.getObject("transaction_id", UUID.class),
						rs.getString("description"), rs.getObject("category_id", UUID.class),
						rs.getBigDecimal("amount")))
				.list();
	}

	/** How much has been refunded against each purchase. Purchases with no refunds are absent from the map. */
	Map<UUID, BigDecimal> refundedAmounts(Collection<UUID> purchaseIds) {
		if (purchaseIds.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
				SELECT refund_of, SUM(amount) AS refunded
				FROM allocations
				WHERE refund_of IN (:purchaseIds)
				GROUP BY refund_of
				""")
				.param("purchaseIds", List.copyOf(purchaseIds))
				.query((rs, rowNum) -> Map.entry(rs.getObject("refund_of", UUID.class), rs.getBigDecimal("refunded")))
				.list()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	/**
	 * Each kind's net total over the pieces reported in the range, so a refund counts in its purchase's month. A null
	 * account means all; otherwise the account is the one the money moved in. Kinds with no pieces are absent.
	 */
	Map<AllocationKind, BigDecimal> totalsReportedBetween(@Nullable UUID accountId, LocalDate from, LocalDate to) {
		Map<AllocationKind, BigDecimal> totals = new EnumMap<>(AllocationKind.class);
		jdbc.sql("""
				SELECT a.kind, SUM(a.amount) AS total
				FROM allocations a
				JOIN transactions t ON t.id = a.transaction_id
				LEFT JOIN transactions rt ON rt.id = a.refund_of
				WHERE %s BETWEEN :from AND :to
				  AND (CAST(:accountId AS uuid) IS NULL OR t.account_id = :accountId)
				GROUP BY a.kind
				""".formatted(REPORTED_ON))
				.param("accountId", accountId)
				.param("from", from)
				.param("to", to)
				.query(rs -> {
					totals.put(AllocationKind.valueOf(rs.getString("kind")), rs.getBigDecimal("total"));
				});
		return totals;
	}

	/**
	 * Purchases dated in the range that still have money left to refund, ignoring what {@code refundId} itself already
	 * refunds since its classification is about to be replaced. Same-account purchases come first, then those that can
	 * cover the whole refund, then the nearest in date.
	 */
	List<RefundCandidate> refundCandidates(Transaction refund, LocalDate from, @Nullable String search, int limit) {
		return jdbc.sql("""
				WITH spent AS (
				    SELECT a.transaction_id, -SUM(a.amount) AS spent,
				           array_agg(DISTINCT a.category_id) FILTER (WHERE a.category_id IS NOT NULL) AS categories
				    FROM allocations a
				    WHERE a.kind = 'EXPENSE' AND a.amount < 0
				    GROUP BY a.transaction_id
				), refunded AS (
				    SELECT refund_of, SUM(amount) AS refunded
				    FROM allocations
				    WHERE refund_of IS NOT NULL AND transaction_id <> :refundId
				    GROUP BY refund_of
				)
				SELECT t.id, t.account_id, t.transaction_date, t.description, s.spent, s.categories,
				       s.spent - COALESCE(r.refunded, 0) AS refundable
				FROM transactions t
				JOIN spent s ON s.transaction_id = t.id
				LEFT JOIN refunded r ON r.refund_of = t.id
				WHERE t.id <> :refundId
				  AND t.transaction_date BETWEEN :from AND :to
				  AND s.spent - COALESCE(r.refunded, 0) > 0
				  AND (CAST(:search AS text) IS NULL OR t.description ILIKE '%' || :search || '%' ESCAPE '\\')
				ORDER BY (t.account_id = :accountId) DESC,
				         (s.spent - COALESCE(r.refunded, 0) >= :amount) DESC,
				         :to - t.transaction_date,
				         t.id
				LIMIT :limit
				""")
				.param("refundId", refund.id())
				.param("accountId", refund.accountId())
				.param("amount", refund.amount().abs())
				.param("from", from)
				.param("to", refund.transactionDate())
				.param("search", search == null ? null : search.replaceAll("([\\\\%_])", "\\\\$1"))
				.param("limit", limit)
				.query(AllocationRepository::mapCandidate)
				.list();
	}

	private static RefundCandidate mapCandidate(ResultSet rs, int rowNum) throws SQLException {
		Array categories = rs.getArray("categories");
		List<UUID> categoryIds = categories == null ? List.of()
				: Arrays.stream((Object[]) categories.getArray()).filter(Objects::nonNull).map(UUID.class::cast).toList();
		return new RefundCandidate(rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
				rs.getObject("transaction_date", LocalDate.class), rs.getString("description"),
				Money.of(rs.getBigDecimal("spent")), Money.of(rs.getBigDecimal("refundable")), categoryIds);
	}

	private static Allocation mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new Allocation(
				rs.getObject("id", UUID.class),
				rs.getObject("transaction_id", UUID.class),
				AllocationKind.valueOf(rs.getString("kind")),
				rs.getObject("category_id", UUID.class),
				rs.getBigDecimal("amount"),
				rs.getString("note"),
				rs.getObject("refund_of", UUID.class));
	}

}
