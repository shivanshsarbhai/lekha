package com.lekha.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.lekha.TestcontainersConfiguration;

@JdbcTest
@Import({ TestcontainersConfiguration.class, AllocationRepository.class, TransactionRepository.class })
class AllocationRepositoryTest {

	@Autowired
	private AllocationRepository repository;

	@Autowired
	private TransactionRepository transactions;

	@Autowired
	private JdbcClient jdbc;

	private UUID dinner;

	private UUID salary;

	private UUID diningOut;

	@BeforeEach
	void createTransactions() {
		UUID account = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO accounts (id, nickname, type, institution, last4, created_at)
				VALUES (:id, 'HDFC Salary', 'BANK', 'HDFC', NULL, now())
				""")
				.param("id", account)
				.update();
		dinner = insertTransaction(account, "UPI-RESTAURANT", "-2000.00");
		salary = insertTransaction(account, "NEFT CR-SALARY", "150000.00");
		diningOut = jdbc.sql("SELECT id FROM categories WHERE name = 'Dining out'").query(UUID.class).single();
	}

	@Test
	void replaceThenFindReturnsTheAllocationsExactly() {
		Allocation food = allocation(dinner, AllocationKind.EXPENSE, diningOut, "-500.00", "My share");
		Allocation lent = allocation(dinner, AllocationKind.LENT, null, "-1500.00", "Rahul, Priya, Aman");

		repository.replace(dinner, List.of(lent, food));

		assertThat(repository.findByTransactionIds(List.of(dinner))).isEqualTo(Map.of(dinner, List.of(food, lent)));
	}

	@Test
	void replaceOverwritesThePreviousSet() {
		repository.replace(dinner, List.of(allocation(dinner, AllocationKind.EXPENSE, diningOut, "-2000.00", null)));
		Allocation lent = allocation(dinner, AllocationKind.LENT, null, "-2000.00", null);

		repository.replace(dinner, List.of(lent));

		assertThat(repository.findByTransactionIds(List.of(dinner))).isEqualTo(Map.of(dinner, List.of(lent)));
	}

	@Test
	void replaceWithNothingMakesTheTransactionUnclassified() {
		repository.replace(dinner, List.of(allocation(dinner, AllocationKind.EXPENSE, diningOut, "-2000.00", null)));

		repository.replace(dinner, List.of());

		assertThat(repository.findByTransactionIds(List.of(dinner))).isEmpty();
	}

	@Test
	void replaceRejectsAnAllocationOfAnotherTransaction() {
		Allocation salaryPiece = allocation(salary, AllocationKind.INCOME, null, "150000.00", null);

		assertThatThrownBy(() -> repository.replace(dinner, List.of(salaryPiece)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void findLoadsSeveralTransactionsInOneCall() {
		Allocation food = allocation(dinner, AllocationKind.EXPENSE, diningOut, "-2000.00", null);
		Allocation pay = allocation(salary, AllocationKind.INCOME, null, "150000.00", null);
		repository.replace(dinner, List.of(food));
		repository.replace(salary, List.of(pay));
		UUID unclassified = UUID.randomUUID();

		assertThat(repository.findByTransactionIds(List.of(dinner, salary, unclassified)))
			.isEqualTo(Map.of(dinner, List.of(food), salary, List.of(pay)));
	}

	@Test
	void findWithNoIdsReturnsNothingWithoutQuerying() {
		assertThat(repository.findByTransactionIds(List.of())).isEmpty();
	}

	@Test
	void databaseRejectsACategoryOnATransfer() {
		assertThatThrownBy(() -> jdbc.sql("""
				INSERT INTO allocations (id, transaction_id, kind, category_id, amount, note)
				VALUES (:id, :transactionId, 'TRANSFER', :categoryId, -2000.00, NULL)
				""")
				.param("id", UUID.randomUUID())
				.param("transactionId", dinner)
				.param("categoryId", diningOut)
				.update()).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("allocations_category_kind_check");
	}

	@Test
	void deletingATransactionDeletesItsAllocations() {
		repository.replace(dinner, List.of(allocation(dinner, AllocationKind.EXPENSE, diningOut, "-2000.00", null)));

		jdbc.sql("DELETE FROM transactions WHERE id = :id").param("id", dinner).update();

		assertThat(repository.findByTransactionIds(List.of(dinner))).isEmpty();
	}

	private UUID insertTransaction(UUID account, String description, String amount) {
		Transaction transaction = Transaction.create(account, LocalDate.of(2026, 9, 1), null, description,
				new BigDecimal(amount), null, null, Map.of());
		transactions.insert(transaction);
		return transaction.id();
	}

	private static Allocation allocation(UUID transactionId, AllocationKind kind, UUID categoryId, String amount,
			String note) {
		return Allocation.create(transactionId, kind, categoryId, new BigDecimal(amount), note);
	}

}
