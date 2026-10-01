package com.lekha.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lekha.TestcontainersConfiguration;

/**
 * Runs outside a test transaction so the service's own transaction really commits or rolls back. That means
 * rows persist between tests, so they are deleted after each one.
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ TestcontainersConfiguration.class, TransactionRepository.class, TransactionService.class })
class TransactionServiceTest {

	@Autowired
	private TransactionService service;

	@Autowired
	private TransactionRepository repository;

	@Autowired
	private JdbcClient jdbc;

	private UUID hdfc;

	@BeforeEach
	void createAccount() {
		hdfc = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO accounts (id, nickname, type, institution, last4, created_at)
				VALUES (:id, 'HDFC Salary', 'BANK', 'HDFC', NULL, now())
				""")
				.param("id", hdfc)
				.update();
	}

	@AfterEach
	void deleteEverything() {
		jdbc.sql("DELETE FROM transactions").update();
		jdbc.sql("DELETE FROM accounts").update();
	}

	@Test
	void recordAllSavesEveryTransaction() {
		Transaction salary = transaction(hdfc, LocalDate.of(2026, 9, 1), "150000.00");
		Transaction rent = transaction(hdfc, LocalDate.of(2026, 9, 5), "-35000.00");

		service.recordAll(List.of(salary, rent));

		assertThat(repository.findByAccountId(hdfc)).containsExactly(salary, rent);
	}

	@Test
	void recordAllSavesNothingWhenOneTransactionFails() {
		Transaction salary = transaction(hdfc, LocalDate.of(2026, 9, 1), "150000.00");
		Transaction orphan = transaction(UUID.randomUUID(), LocalDate.of(2026, 9, 5), "-35000.00");

		assertThatThrownBy(() -> service.recordAll(List.of(salary, orphan)))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(repository.findByAccountId(hdfc)).isEmpty();
	}

	private static Transaction transaction(UUID accountId, LocalDate date, String amount) {
		return Transaction.create(accountId, date, null, "UPI-TEST", new BigDecimal(amount), null, null, Map.of());
	}

}
