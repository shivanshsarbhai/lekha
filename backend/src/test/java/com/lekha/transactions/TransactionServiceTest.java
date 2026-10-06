package com.lekha.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lekha.TestcontainersConfiguration;
import com.lekha.accounts.AccountNotFoundException;
import com.lekha.accounts.AccountService;

/**
 * Runs outside a test transaction so the service's own transaction really commits or rolls back. That means
 * rows persist between tests, so they are deleted after each one.
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ TestcontainersConfiguration.class, TransactionRepository.class, AllocationRepository.class,
		TransactionService.class })
class TransactionServiceTest {

	@Autowired
	private TransactionService service;

	@Autowired
	private TransactionRepository repository;

	@Autowired
	private AllocationRepository allocations;

	@Autowired
	private JdbcClient jdbc;

	@MockitoBean
	private AccountService accounts;

	private UUID hdfc;

	@BeforeEach
	void createAccount() {
		hdfc = insertAccount("HDFC Salary");
	}

	@AfterEach
	void deleteEverything() {
		jdbc.sql("DELETE FROM transactions").update();
		jdbc.sql("DELETE FROM accounts").update();
	}

	@Test
	void recordNewSavesEveryTransaction() {
		Transaction salary = transaction(hdfc, LocalDate.of(2026, 9, 1), "150000.00");
		Transaction rent = transaction(hdfc, LocalDate.of(2026, 9, 5), "-35000.00");

		assertThat(service.recordNew(List.of(salary, rent))).containsExactly(salary, rent);

		assertThat(repository.findByAccountId(hdfc)).containsExactly(salary, rent);
	}

	@Test
	void recordNewSavesNothingWhenOneTransactionFails() {
		Transaction salary = transaction(hdfc, LocalDate.of(2026, 9, 1), "150000.00");
		Transaction orphan = transaction(UUID.randomUUID(), LocalDate.of(2026, 9, 5), "-35000.00");

		assertThatThrownBy(() -> service.recordNew(List.of(salary, orphan)))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(repository.findByAccountId(hdfc)).isEmpty();
	}

	@Test
	void recordNewSkipsTransactionsAlreadyStored() {
		Transaction salary = statementRow(LocalDate.of(2026, 9, 1), "150000.00", "160000.00", "REF1", "NEFT CR");
		Transaction rent = statementRow(LocalDate.of(2026, 9, 5), "-35000.00", "125000.00", "REF2", "UPI-RENT");
		service.recordNew(List.of(salary, rent));

		Transaction salaryAgain = statementRow(LocalDate.of(2026, 9, 1), "150000.00", "160000.00", "REF1", "NEFT CR");
		Transaction swiggy = statementRow(LocalDate.of(2026, 9, 6), "-450.00", "124550.00", "REF3", "UPI-SWIGGY");

		assertThat(service.recordNew(List.of(salaryAgain, swiggy))).containsExactly(swiggy);
		assertThat(repository.findByAccountId(hdfc)).containsExactly(salary, rent, swiggy);
	}

	@Test
	void recordNewTreatsARowWithAnImprovedDescriptionAsAlreadyStored() {
		service.recordNew(List.of(statementRow(LocalDate.of(2026, 9, 1), "-450.00", "9550.00", "REF1", "UPI-SWIG")));

		Transaction reparsed = statementRow(LocalDate.of(2026, 9, 1), "-450.00", "9550.00", "REF1", "UPI-SWIGGY");

		assertThat(service.recordNew(List.of(reparsed))).isEmpty();
	}

	@Test
	void recordNewKeepsGenuineRepeatsThatAreNotStoredYet() {
		Transaction chai = transaction(hdfc, LocalDate.of(2026, 9, 1), "-10.00");
		service.recordNew(List.of(chai));

		Transaction sameChai = transaction(hdfc, LocalDate.of(2026, 9, 1), "-10.00");
		Transaction secondChai = transaction(hdfc, LocalDate.of(2026, 9, 1), "-10.00");

		assertThat(service.recordNew(List.of(sameChai, secondChai))).containsExactly(secondChai);
		assertThat(repository.findByAccountId(hdfc)).hasSize(2);
	}

	@Test
	void listReturnsTheRangeNewestFirstWithExactTotals() {
		service.recordNew(List.of(transaction(hdfc, LocalDate.of(2026, 9, 1), "150000.00"),
				transaction(hdfc, LocalDate.of(2026, 9, 3), "-450.10"),
				transaction(hdfc, LocalDate.of(2026, 9, 5), "-35000.20"),
				transaction(hdfc, LocalDate.of(2026, 10, 1), "-99.00")));

		TransactionList list = service.list(null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

		assertThat(list.transactions())
				.extracting(listed -> listed.transaction().transactionDate())
				.containsExactly(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 1));
		assertThat(list.moneyIn()).isEqualTo(new BigDecimal("150000.00"));
		assertThat(list.moneyOut()).isEqualTo(new BigDecimal("-35450.30"));
		assertThat(list.net()).isEqualTo(new BigDecimal("114549.70"));
	}

	@Test
	void listOfNothingHasZeroTotals() {
		TransactionList list = service.list(null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

		assertThat(list.transactions()).isEmpty();
		assertThat(List.of(list.moneyIn(), list.moneyOut(), list.net(), list.spending(), list.income(),
				list.invested(), list.lent()))
			.containsOnly(new BigDecimal("0.00"));
		assertThat(list.unclassifiedCount()).isZero();
	}

	@Test
	void listAttachesEachTransactionsAllocations() {
		Transaction dinner = transaction(hdfc, LocalDate.of(2026, 9, 3), "-2000.00");
		Transaction chai = transaction(hdfc, LocalDate.of(2026, 9, 4), "-20.00");
		service.recordNew(List.of(dinner, chai));
		Allocation food = allocation(dinner, AllocationKind.EXPENSE, "-500.00");
		Allocation lent = allocation(dinner, AllocationKind.LENT, "-1500.00");
		allocations.replace(dinner.id(), List.of(food, lent));

		TransactionList list = service.list(null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

		assertThat(list.transactions()).containsExactly(new ListedTransaction(chai, List.of()),
				new ListedTransaction(dinner, List.of(food, lent)));
		assertThat(list.unclassifiedCount()).isEqualTo(1);
	}

	@Test
	void listTotalsCountOnlyWhatEachPieceReallyIs() {
		Transaction salary = transaction(hdfc, LocalDate.of(2026, 9, 1), "150000.00");
		Transaction dinner = transaction(hdfc, LocalDate.of(2026, 9, 3), "-2000.00");
		Transaction refund = transaction(hdfc, LocalDate.of(2026, 9, 4), "199.00");
		Transaction sip = transaction(hdfc, LocalDate.of(2026, 9, 5), "-10000.00");
		Transaction toSavings = transaction(hdfc, LocalDate.of(2026, 9, 6), "-50000.00");
		Transaction repaid = transaction(hdfc, LocalDate.of(2026, 9, 7), "500.00");
		Transaction unclassified = transaction(hdfc, LocalDate.of(2026, 9, 8), "-300.00");
		service.recordNew(List.of(salary, dinner, refund, sip, toSavings, repaid, unclassified));
		classify(salary, allocation(salary, AllocationKind.INCOME, "150000.00"));
		classify(dinner, allocation(dinner, AllocationKind.EXPENSE, "-500.00"),
				allocation(dinner, AllocationKind.LENT, "-1500.00"));
		classify(refund, allocation(refund, AllocationKind.EXPENSE, "199.00"));
		classify(sip, allocation(sip, AllocationKind.INVESTMENT, "-10000.00"));
		classify(toSavings, allocation(toSavings, AllocationKind.TRANSFER, "-50000.00"));
		classify(repaid, allocation(repaid, AllocationKind.LENT, "500.00"));

		TransactionList list = service.list(null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

		assertThat(list.spending()).isEqualTo(new BigDecimal("301.00"));
		assertThat(list.income()).isEqualTo(new BigDecimal("150000.00"));
		assertThat(list.invested()).isEqualTo(new BigDecimal("10000.00"));
		assertThat(list.lent()).isEqualTo(new BigDecimal("1000.00"));
		assertThat(list.unclassifiedCount()).isEqualTo(1);
		assertThat(list.moneyOut()).isEqualTo(new BigDecimal("-62300.00"));
	}

	@Test
	void listCanBeLimitedToOneAccount() {
		UUID scapia = insertAccount("Scapia Card");
		service.recordNew(List.of(transaction(hdfc, LocalDate.of(2026, 9, 1), "150000.00"),
				transaction(scapia, LocalDate.of(2026, 9, 2), "-2000.00")));

		TransactionList list = service.list(scapia, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

		assertThat(list.transactions()).extracting(listed -> listed.transaction().accountId()).containsExactly(scapia);
		assertThat(list.moneyIn()).isEqualTo(new BigDecimal("0.00"));
		assertThat(list.moneyOut()).isEqualTo(new BigDecimal("-2000.00"));
	}

	@Test
	void listRejectsAnUnknownAccount() {
		UUID unknown = UUID.randomUUID();
		given(accounts.getAccount(unknown)).willThrow(new AccountNotFoundException(unknown));

		assertThatThrownBy(() -> service.list(unknown, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
				.isInstanceOf(AccountNotFoundException.class);
	}

	@Test
	void listRejectsFromAfterTo() {
		assertThatThrownBy(() -> service.list(null, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
				.isInstanceOf(InvalidDateRangeException.class)
				.hasMessage("\"from\" (2026-09-30) must not be after \"to\" (2026-09-01)");
	}

	@Test
	void listAllowsAtMostOneYear() {
		assertThat(service.list(null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).transactions()).isEmpty();

		assertThatThrownBy(() -> service.list(null, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)))
				.isInstanceOf(InvalidDateRangeException.class)
				.hasMessage("The date range must be at most 1 year");
	}

	private UUID insertAccount(String nickname) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO accounts (id, nickname, type, institution, last4, created_at)
				VALUES (:id, :nickname, 'BANK', 'HDFC', NULL, now())
				""")
				.param("id", id)
				.param("nickname", nickname)
				.update();
		return id;
	}

	private void classify(Transaction transaction, Allocation... pieces) {
		allocations.replace(transaction.id(), List.of(pieces));
	}

	private static Allocation allocation(Transaction transaction, AllocationKind kind, String amount) {
		return Allocation.create(transaction.id(), kind, null, new BigDecimal(amount), null);
	}

	private static Transaction transaction(UUID accountId, LocalDate date, String amount) {
		return Transaction.create(accountId, date, null, "UPI-TEST", new BigDecimal(amount), null, null, Map.of());
	}

	private Transaction statementRow(LocalDate date, String amount, String balance, String reference,
			String description) {
		return Transaction.create(hdfc, date, null, description, new BigDecimal(amount), new BigDecimal(balance),
				null, Map.of("reference", reference));
	}

}
