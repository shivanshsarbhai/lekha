package com.lekha.transactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AllocationRepository {

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
				SELECT id, transaction_id, kind, category_id, amount, note
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

	/** Swaps a transaction's allocations for a new set. Call inside a database transaction so it is all-or-nothing. */
	void replace(UUID transactionId, List<Allocation> allocations) {
		for (Allocation allocation : allocations) {
			if (!allocation.transactionId().equals(transactionId)) {
				throw new IllegalArgumentException("Allocation " + allocation.id() + " belongs to another transaction");
			}
		}

		jdbc.sql("DELETE FROM allocations WHERE transaction_id = :transactionId")
				.param("transactionId", transactionId)
				.update();
		for (Allocation allocation : allocations) {
			jdbc.sql("""
					INSERT INTO allocations (id, transaction_id, kind, category_id, amount, note)
					VALUES (:id, :transactionId, :kind, :categoryId, :amount, :note)
					""")
					.param("id", allocation.id())
					.param("transactionId", allocation.transactionId())
					.param("kind", allocation.kind().name())
					.param("categoryId", allocation.categoryId())
					.param("amount", allocation.amount())
					.param("note", allocation.note())
					.update();
		}
	}

	private static Allocation mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new Allocation(
				rs.getObject("id", UUID.class),
				rs.getObject("transaction_id", UUID.class),
				AllocationKind.valueOf(rs.getString("kind")),
				rs.getObject("category_id", UUID.class),
				rs.getBigDecimal("amount"),
				rs.getString("note"));
	}

}
