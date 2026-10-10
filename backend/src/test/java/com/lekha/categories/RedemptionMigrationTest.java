package com.lekha.categories;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lekha.TestcontainersConfiguration;

/**
 * Builds a database as it was before V9 in a schema of its own, saves redemptions the old way, then runs V9. Not
 * transactional, because Flyway migrates on its own connection and has to see the rows.
 */
@JdbcTest
@Import(TestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RedemptionMigrationTest {

	private static final String SCHEMA = "before_v9";

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		jdbc.sql("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE").update();
	}

	@Test
	void redemptionsBecomeMutualFundInvestmentsAndTheTransferCategoryGoes() {
		migrateTo("8");
		UUID redemption = categoryId("Mutual fund redemption", null);
		UUID swp = insertCategory("Test SWP", redemption);
		UUID bankToBank = categoryId("Bank to bank transfer", null);
		UUID account = insertAccount();
		UUID sale = allocate(account, "TRANSFER", redemption, "20000.00");
		UUID withdrawal = allocate(account, "TRANSFER", swp, "5000.00");
		UUID move = allocate(account, "TRANSFER", bankToBank, "-3000.00");

		migrateTo("9");

		UUID mutualFunds = categoryId("Mutual funds", null);
		assertThat(piece(sale)).isEqualTo(new Piece("INVESTMENT", mutualFunds, new BigDecimal("20000.00")));
		assertThat(piece(withdrawal)).isEqualTo(new Piece("INVESTMENT", mutualFunds, new BigDecimal("5000.00")));
		assertThat(piece(move)).isEqualTo(new Piece("TRANSFER", bankToBank, new BigDecimal("-3000.00")));
		assertThat(categoryNames()).doesNotContain("Mutual fund redemption", "Test SWP")
			.contains("Mutual funds", "Bank to bank transfer");
	}

	@Test
	void aRenamedRedemptionCategoryIsLeftAlone() {
		migrateTo("8");
		UUID redemption = categoryId("Mutual fund redemption", null);
		jdbc.sql("UPDATE " + SCHEMA + ".categories SET name = 'Fund sales' WHERE id = :id")
			.param("id", redemption)
			.update();
		UUID sale = allocate(insertAccount(), "TRANSFER", redemption, "20000.00");

		migrateTo("9");

		assertThat(piece(sale)).isEqualTo(new Piece("TRANSFER", redemption, new BigDecimal("20000.00")));
	}

	record Piece(String kind, @Nullable UUID categoryId, BigDecimal amount) {
	}

	private void migrateTo(String version) {
		Flyway.configure().dataSource(dataSource).schemas(SCHEMA).target(version).load().migrate();
	}

	private UUID categoryId(String name, @Nullable UUID parentId) {
		return jdbc.sql("SELECT id FROM " + SCHEMA + """
				.categories WHERE name = :name AND parent_id IS NOT DISTINCT FROM CAST(:parentId AS uuid)
				""")
			.param("name", name)
			.param("parentId", parentId)
			.query(UUID.class)
			.single();
	}

	private List<String> categoryNames() {
		return jdbc.sql("SELECT name FROM " + SCHEMA + ".categories").query(String.class).list();
	}

	private UUID insertCategory(String name, UUID parentId) {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO " + SCHEMA + """
				.categories (id, parent_id, name, kind, created_at)
				VALUES (:id, :parentId, :name, 'TRANSFER', now())
				""").param("id", id).param("parentId", parentId).param("name", name).update();
		return id;
	}

	private UUID insertAccount() {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO " + SCHEMA + """
				.accounts (id, nickname, type, institution, last4, created_at)
				VALUES (:id, 'HDFC Salary', 'BANK', 'HDFC', NULL, now())
				""").param("id", id).update();
		return id;
	}

	/** A transaction classified whole as one piece. Returns the piece's id. */
	private UUID allocate(UUID accountId, String kind, UUID categoryId, String amount) {
		UUID transaction = UUID.randomUUID();
		jdbc.sql("INSERT INTO " + SCHEMA + """
				.transactions (id, account_id, transaction_date, description, amount, metadata, created_at)
				VALUES (:id, :accountId, :date, 'TEST', :amount, '{}'::jsonb, now())
				""")
			.param("id", transaction)
			.param("accountId", accountId)
			.param("date", LocalDate.of(2026, 9, 1))
			.param("amount", new BigDecimal(amount))
			.update();
		UUID piece = UUID.randomUUID();
		jdbc.sql("INSERT INTO " + SCHEMA + """
				.allocations (id, transaction_id, kind, category_id, amount)
				VALUES (:id, :transactionId, :kind, :categoryId, :amount)
				""")
			.param("id", piece)
			.param("transactionId", transaction)
			.param("kind", kind)
			.param("categoryId", categoryId)
			.param("amount", new BigDecimal(amount))
			.update();
		return piece;
	}

	private Piece piece(UUID id) {
		return jdbc.sql("SELECT kind, category_id, amount FROM " + SCHEMA + ".allocations WHERE id = :id")
			.param("id", id)
			.query(Piece.class)
			.single();
	}

}
