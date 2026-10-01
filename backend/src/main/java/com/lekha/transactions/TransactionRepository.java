package com.lekha.transactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
class TransactionRepository {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final TypeReference<Map<String, String>> METADATA_TYPE = new TypeReference<>() {
	};

	private final JdbcClient jdbc;

	TransactionRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	void insert(Transaction transaction) {
		jdbc.sql("""
				INSERT INTO transactions (id, account_id, transaction_date, settlement_date, description, amount,
				                          balance_after, payment_mode, metadata, created_at)
				VALUES (:id, :accountId, :transactionDate, :settlementDate, :description, :amount,
				        :balanceAfter, :paymentMode, CAST(:metadata AS jsonb), :createdAt)
				""")
				.param("id", transaction.id())
				.param("accountId", transaction.accountId())
				.param("transactionDate", transaction.transactionDate())
				.param("settlementDate", transaction.settlementDate())
				.param("description", transaction.description())
				.param("amount", transaction.amount())
				.param("balanceAfter", transaction.balanceAfter())
				.param("paymentMode", transaction.paymentMode() == null ? null : transaction.paymentMode().name())
				.param("metadata", JSON.writeValueAsString(transaction.metadata()))
				.param("createdAt", OffsetDateTime.ofInstant(transaction.createdAt(), ZoneOffset.UTC))
				.update();
	}

	List<Transaction> findByAccountId(UUID accountId) {
		return jdbc.sql("""
				SELECT id, account_id, transaction_date, settlement_date, description, amount,
				       balance_after, payment_mode, metadata, created_at
				FROM transactions
				WHERE account_id = :accountId
				ORDER BY transaction_date, created_at, id
				""")
				.param("accountId", accountId)
				.query(TransactionRepository::mapRow)
				.list();
	}

	/** Transactions dated from {@code from} to {@code to}, both inclusive, newest first. A null account means all. */
	List<Transaction> findBetween(@Nullable UUID accountId, LocalDate from, LocalDate to) {
		return jdbc.sql("""
				SELECT id, account_id, transaction_date, settlement_date, description, amount,
				       balance_after, payment_mode, metadata, created_at
				FROM transactions
				WHERE (CAST(:accountId AS uuid) IS NULL OR account_id = :accountId)
				  AND transaction_date BETWEEN :from AND :to
				ORDER BY transaction_date DESC, created_at DESC, id DESC
				""")
				.param("accountId", accountId)
				.param("from", from)
				.param("to", to)
				.query(TransactionRepository::mapRow)
				.list();
	}

	private static Transaction mapRow(ResultSet rs, int rowNum) throws SQLException {
		String paymentMode = rs.getString("payment_mode");
		return new Transaction(
				rs.getObject("id", UUID.class),
				rs.getObject("account_id", UUID.class),
				rs.getObject("transaction_date", LocalDate.class),
				rs.getObject("settlement_date", LocalDate.class),
				rs.getString("description"),
				rs.getBigDecimal("amount"),
				rs.getBigDecimal("balance_after"),
				paymentMode == null ? null : PaymentMode.valueOf(paymentMode),
				JSON.readValue(rs.getString("metadata"), METADATA_TYPE),
				rs.getObject("created_at", OffsetDateTime.class).toInstant());
	}

}
