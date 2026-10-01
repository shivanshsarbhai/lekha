package com.lekha.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
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
@Import({ TestcontainersConfiguration.class, TransactionRepository.class })
class TransactionRepositoryTest {

	@Autowired
	private TransactionRepository repository;

	@Autowired
	private JdbcClient jdbc;

	private UUID hdfc;

	private UUID scapia;

	@BeforeEach
	void createAccounts() {
		hdfc = insertAccount("HDFC Salary", "BANK", "HDFC");
		scapia = insertAccount("Scapia Card", "CREDIT_CARD", "FEDERAL_BANK");
	}

	@Test
	void insertStoresEveryFieldExactly() {
		Transaction full = Transaction.create(hdfc, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2),
				"UPI-SWIGGY-swiggy@icici-ICIC0000001-123456789012-Order", new BigDecimal("-450.00"),
				new BigDecimal("82310.55"), PaymentMode.UPI,
				Map.of("reference", "123456789012", "upiId", "swiggy@icici", "counterparty", "SWIGGY"));
		Transaction minimal = Transaction.create(hdfc, LocalDate.of(2026, 9, 3), null, "BANK CHARGES",
				new BigDecimal("-17.70"), null, null, Map.of());

		repository.insert(full);
		repository.insert(minimal);

		assertThat(repository.findByAccountId(hdfc)).containsExactly(full, minimal);
	}

	@Test
	void findByAccountIdReturnsOnlyThatAccountOldestFirst() {
		Transaction salary = transaction(hdfc, LocalDate.of(2026, 9, 1), "NEFT CR-ACME CORP-SALARY", "150000.00");
		Transaction rent = transaction(hdfc, LocalDate.of(2026, 9, 5), "UPI-LANDLORD-RENT", "-35000.00");
		Transaction swiggy = transaction(hdfc, LocalDate.of(2026, 9, 3), "UPI-SWIGGY", "-450.00");
		Transaction cardSpend = transaction(scapia, LocalDate.of(2026, 9, 2), "AMAZON", "-2000.00");
		repository.insert(rent);
		repository.insert(salary);
		repository.insert(swiggy);
		repository.insert(cardSpend);

		assertThat(repository.findByAccountId(hdfc))
				.extracting(Transaction::description)
				.containsExactly("NEFT CR-ACME CORP-SALARY", "UPI-SWIGGY", "UPI-LANDLORD-RENT");
	}

	@Test
	void findBetweenReturnsEveryAccountInTheRangeNewestFirst() {
		repository.insert(transaction(hdfc, LocalDate.of(2026, 8, 31), "UPI-AUGUST", "-100.00"));
		repository.insert(transaction(hdfc, LocalDate.of(2026, 9, 1), "NEFT CR-SALARY", "150000.00"));
		repository.insert(transaction(scapia, LocalDate.of(2026, 9, 15), "AMAZON", "-2000.00"));
		repository.insert(transaction(hdfc, LocalDate.of(2026, 9, 30), "UPI-RENT", "-35000.00"));
		repository.insert(transaction(hdfc, LocalDate.of(2026, 10, 1), "UPI-OCTOBER", "-200.00"));

		assertThat(repository.findBetween(null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
				.extracting(Transaction::description)
				.containsExactly("UPI-RENT", "AMAZON", "NEFT CR-SALARY");
	}

	@Test
	void findBetweenCanBeLimitedToOneAccount() {
		repository.insert(transaction(hdfc, LocalDate.of(2026, 9, 1), "NEFT CR-SALARY", "150000.00"));
		repository.insert(transaction(scapia, LocalDate.of(2026, 9, 15), "AMAZON", "-2000.00"));

		assertThat(repository.findBetween(scapia, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
				.extracting(Transaction::description)
				.containsExactly("AMAZON");
	}

	@Test
	void insertRejectsTransactionForUnknownAccount() {
		Transaction orphan = transaction(UUID.randomUUID(), LocalDate.of(2026, 9, 1), "UPI-SWIGGY", "-450.00");

		assertThatThrownBy(() -> repository.insert(orphan))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("transactions_account_id_fkey");
	}

	private static Transaction transaction(UUID accountId, LocalDate date, String description, String amount) {
		return Transaction.create(accountId, date, null, description, new BigDecimal(amount), null, null, Map.of());
	}

	private UUID insertAccount(String nickname, String type, String institution) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO accounts (id, nickname, type, institution, last4, created_at)
				VALUES (:id, :nickname, :type, :institution, NULL, now())
				""")
				.param("id", id)
				.param("nickname", nickname)
				.param("type", type)
				.param("institution", institution)
				.update();
		return id;
	}

}
