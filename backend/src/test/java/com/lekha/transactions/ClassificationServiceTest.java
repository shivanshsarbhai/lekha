package com.lekha.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.lekha.TestcontainersConfiguration;
import com.lekha.categories.Category;
import com.lekha.categories.CategoryKind;
import com.lekha.categories.CategoryService;

/**
 * Categories come from a mock because their repository is private to the categories package. The mock returns the
 * real seeded ids so the allocations' foreign key is still satisfied.
 */
@JdbcTest
@Import({ TestcontainersConfiguration.class, TransactionRepository.class, AllocationRepository.class,
		ClassificationService.class })
class ClassificationServiceTest {

	@Autowired
	private ClassificationService service;

	@Autowired
	private TransactionRepository transactions;

	@Autowired
	private AllocationRepository allocations;

	@Autowired
	private JdbcClient jdbc;

	@MockitoBean
	private CategoryService categories;

	private UUID account;

	private UUID diningOut;

	private UUID salary;

	private UUID cardBillPayment;

	@BeforeEach
	void setUp() {
		account = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO accounts (id, nickname, type, institution, last4, created_at)
				VALUES (:id, 'HDFC Salary', 'BANK', 'HDFC', NULL, now())
				""").param("id", account).update();
		diningOut = stubCategory("Dining out", CategoryKind.EXPENSE);
		salary = stubCategory("Salary", CategoryKind.INCOME);
		cardBillPayment = stubCategory("Credit card bill payment", CategoryKind.TRANSFER);
	}

	@Test
	void savesASplitAndReturnsItInDisplayOrder() {
		UUID dinner = insertTransaction("-2000.00");

		List<Allocation> saved = service.classify(dinner,
				List.of(request(AllocationKind.LENT, null, "-1500.00", "Rahul, Priya"),
						request(AllocationKind.EXPENSE, diningOut, "-500.00", "My share")));

		assertThat(saved).extracting(Allocation::kind).containsExactly(AllocationKind.EXPENSE, AllocationKind.LENT);
		assertThat(allocations.findByTransactionIds(List.of(dinner))).isEqualTo(Map.of(dinner, saved));
	}

	@Test
	void savingAgainReplacesTheOldSplit() {
		UUID dinner = insertTransaction("-2000.00");
		service.classify(dinner, List.of(request(AllocationKind.EXPENSE, diningOut, "-2000.00", null)));

		List<Allocation> saved = service.classify(dinner,
				List.of(request(AllocationKind.TRANSFER, null, "-2000.00", null)));

		assertThat(allocations.findByTransactionIds(List.of(dinner))).isEqualTo(Map.of(dinner, saved));
	}

	@Test
	void anEmptyListMakesTheTransactionUnclassified() {
		UUID dinner = insertTransaction("-2000.00");
		service.classify(dinner, List.of(request(AllocationKind.EXPENSE, diningOut, "-2000.00", null)));

		assertThat(service.classify(dinner, List.of())).isEmpty();

		assertThat(allocations.findByTransactionIds(List.of(dinner))).isEmpty();
	}

	@Test
	void acceptsARefundAsAPositiveExpense() {
		UUID refund = insertTransaction("799.00");

		assertThat(service.classify(refund, List.of(request(AllocationKind.EXPENSE, diningOut, "799.00", null))))
			.hasSize(1);
	}

	@Test
	void rejectsAnUnknownTransaction() {
		UUID unknown = UUID.randomUUID();

		assertThatThrownBy(() -> service.classify(unknown, List.of()))
			.isInstanceOf(TransactionNotFoundException.class)
			.hasMessage("No transaction with id " + unknown);
	}

	@Test
	void rejectsARowWithoutAKindOrAmount() {
		UUID dinner = insertTransaction("-2000.00");

		assertThatThrownBy(() -> service.classify(dinner, List.of(request(null, null, "-2000.00", null))))
			.isInstanceOf(InvalidAllocationException.class)
			.hasMessage("Every allocation needs a kind and an amount");
		assertThatThrownBy(() -> service.classify(dinner, Arrays.asList((AllocationRequest) null)))
			.isInstanceOf(InvalidAllocationException.class)
			.hasMessage("Every allocation needs a kind and an amount");
	}

	@Test
	void turnsTheAllocationsOwnRulesIntoA400() {
		UUID dinner = insertTransaction("-2000.00");

		assertThatThrownBy(() -> service.classify(dinner,
				List.of(request(AllocationKind.LENT, diningOut, "-2000.00", null))))
			.isInstanceOf(InvalidAllocationException.class)
			.hasMessage("LENT allocations can't have a category");
	}

	@Test
	void savesATransferWithItsCategory() {
		UUID billPayment = insertTransaction("-25000.00");

		assertThat(service.classify(billPayment,
				List.of(request(AllocationKind.TRANSFER, cardBillPayment, "-25000.00", null))))
			.extracting(Allocation::categoryId)
			.containsExactly(cardBillPayment);
	}

	@Test
	void rejectsAnExpenseCategoryOnATransfer() {
		UUID dinner = insertTransaction("-2000.00");

		assertThatThrownBy(() -> service.classify(dinner,
				List.of(request(AllocationKind.TRANSFER, diningOut, "-2000.00", null))))
			.isInstanceOf(InvalidAllocationException.class)
			.hasMessage("\"Dining out\" is an EXPENSE category, so it can't be used for a TRANSFER allocation");
	}

	@Test
	void rejectsAnUnknownCategory() {
		UUID dinner = insertTransaction("-2000.00");
		UUID unknown = UUID.randomUUID();
		given(categories.findCategory(unknown)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.classify(dinner,
				List.of(request(AllocationKind.EXPENSE, unknown, "-2000.00", null))))
			.isInstanceOf(InvalidAllocationException.class)
			.hasMessage("No category with id " + unknown);
	}

	@Test
	void rejectsACategoryOfAnotherKind() {
		UUID dinner = insertTransaction("-2000.00");

		assertThatThrownBy(() -> service.classify(dinner,
				List.of(request(AllocationKind.EXPENSE, salary, "-2000.00", null))))
			.isInstanceOf(InvalidAllocationException.class)
			.hasMessage("\"Salary\" is an INCOME category, so it can't be used for an EXPENSE allocation");
	}

	@Test
	void rejectsPiecesThatDoNotAddUp() {
		UUID dinner = insertTransaction("-2000.00");

		assertThatThrownBy(() -> service.classify(dinner,
				List.of(request(AllocationKind.EXPENSE, diningOut, "-500.00", null),
						request(AllocationKind.LENT, null, "-1300.00", null))))
			.isInstanceOf(InvalidAllocationException.class)
			.hasMessage("Allocations add up to -1800.00 but the transaction is -2000.00");
		assertThat(allocations.findByTransactionIds(List.of(dinner))).isEmpty();
	}

	@Test
	void linksARefundToThePurchaseItCameBackFrom() {
		UUID purchase = insertPurchase("-2000.00");
		UUID refund = insertTransaction("800.00");

		List<Allocation> saved = service.classify(refund, List.of(refund(diningOut, "800.00", purchase)));

		assertThat(saved).extracting(Allocation::refundOf).containsExactly(purchase);
		assertThat(allocations.refundedAmounts(List.of(purchase))).containsEntry(purchase, new BigDecimal("800.00"));
	}

	@Test
	void severalPartialRefundsCanAddUpToThePurchase() {
		UUID purchase = insertPurchase("-2000.00");
		service.classify(insertTransaction("1200.00"), List.of(refund(diningOut, "1200.00", purchase)));

		service.classify(insertTransaction("800.00"), List.of(refund(diningOut, "800.00", purchase)));

		assertThat(allocations.refundedAmounts(List.of(purchase))).containsEntry(purchase, new BigDecimal("2000.00"));
	}

	@Test
	void rejectsRefundsAddingUpToMoreThanWasSpent() {
		UUID purchase = insertPurchase("-2000.00");
		service.classify(insertTransaction("1500.00"), List.of(refund(diningOut, "1500.00", purchase)));
		UUID second = insertTransaction("600.00");

		assertThatThrownBy(() -> service.classify(second, List.of(refund(diningOut, "600.00", purchase))))
			.isInstanceOf(InvalidAllocationException.class)
			.hasMessage("Refunds of \"SWIGGY ORDER\" on 2026-08-20 would add up to 2100.00, more than the 2000.00 "
					+ "spent on it");
	}

	@Test
	void savingARefundAgainDoesNotCountItsOldPieces() {
		UUID purchase = insertPurchase("-2000.00");
		UUID refund = insertTransaction("2000.00");
		service.classify(refund, List.of(refund(diningOut, "2000.00", purchase)));

		assertThat(service.classify(refund, List.of(refund(diningOut, "2000.00", purchase)))).hasSize(1);
	}

	@Test
	void onlyTheSpentPartOfASplitPurchaseCanBeRefunded() {
		UUID dinner = insertPurchase("-2000.00", request(AllocationKind.EXPENSE, diningOut, "-500.00", null),
				request(AllocationKind.LENT, null, "-1500.00", null));
		UUID refund = insertTransaction("1000.00");

		assertThatThrownBy(() -> service.classify(refund, List.of(refund(diningOut, "1000.00", dinner))))
			.isInstanceOf(InvalidAllocationException.class)
			.hasMessageContaining("more than the 500.00 spent on it");
	}

	@Test
	void rejectsARefundInAnotherCategory() {
		UUID purchase = insertPurchase("-2000.00");
		UUID groceries = stubCategory("Groceries", CategoryKind.EXPENSE);
		UUID refund = insertTransaction("800.00");

		assertThatThrownBy(() -> service.classify(refund, List.of(refund(groceries, "800.00", purchase))))
			.isInstanceOf(InvalidAllocationException.class)
			.hasMessage("A refund must use the same category as the purchase it refunds, \"SWIGGY ORDER\" on 2026-08-20");
	}

	@Test
	void rejectsRefundingSomethingThatIsNotAnExpense() {
		UUID transfer = insertTransaction("-2000.00");
		service.classify(transfer, List.of(request(AllocationKind.TRANSFER, null, "-2000.00", null)));
		UUID refund = insertTransaction("800.00");

		assertThatThrownBy(() -> service.classify(refund, List.of(refund(diningOut, "800.00", transfer))))
			.isInstanceOf(InvalidAllocationException.class)
			.hasMessage("\"UPI-SOMETHING\" on 2026-09-01 isn't classified as an expense, so it can't be refunded");
	}

	@Test
	void rejectsRefundingAnUnknownTransaction() {
		UUID refund = insertTransaction("800.00");
		UUID unknown = UUID.randomUUID();

		assertThatThrownBy(() -> service.classify(refund, List.of(refund(diningOut, "800.00", unknown))))
			.isInstanceOf(InvalidAllocationException.class)
			.hasMessage("No transaction with id " + unknown + " to refund");
	}

	@Test
	void aRefundedPurchaseCannotBeReclassifiedBelowItsRefunds() {
		UUID purchase = insertPurchase("-2000.00");
		service.classify(insertTransaction("1500.00"), List.of(refund(diningOut, "1500.00", purchase)));

		assertThatThrownBy(() -> service.classify(purchase,
				List.of(request(AllocationKind.EXPENSE, diningOut, "-1000.00", null),
						request(AllocationKind.LENT, null, "-1000.00", null))))
			.isInstanceOf(RefundConflictException.class)
			.hasMessage("1500.00 of this purchase was refunded by \"UPI-SOMETHING\", so it must stay an expense of at "
					+ "least that much. Unlink the refund first.");
		assertThatThrownBy(() -> service.classify(purchase, List.of())).isInstanceOf(RefundConflictException.class);
	}

	@Test
	void aRefundedPurchaseCannotLeaveTheRefundsCategory() {
		UUID purchase = insertPurchase("-2000.00");
		UUID groceries = stubCategory("Groceries", CategoryKind.EXPENSE);
		service.classify(insertTransaction("500.00"), List.of(refund(diningOut, "500.00", purchase)));

		assertThatThrownBy(() -> service.classify(purchase,
				List.of(request(AllocationKind.EXPENSE, groceries, "-2000.00", null))))
			.isInstanceOf(RefundConflictException.class)
			.hasMessageContaining("in a category it would no longer have");
	}

	@Test
	void aRefundedPurchaseCanStillBeSplitIfItKeepsEnoughSpending() {
		UUID purchase = insertPurchase("-2000.00");
		service.classify(insertTransaction("500.00"), List.of(refund(diningOut, "500.00", purchase)));

		assertThat(service.classify(purchase, List.of(request(AllocationKind.EXPENSE, diningOut, "-500.00", null),
				request(AllocationKind.LENT, null, "-1500.00", null))))
			.hasSize(2);
	}

	@Test
	void deletingThePurchaseKeepsTheRefundAsAPlainExpense() {
		UUID purchase = insertPurchase("-2000.00");
		UUID refund = insertTransaction("800.00");
		service.classify(refund, List.of(refund(diningOut, "800.00", purchase)));

		jdbc.sql("DELETE FROM transactions WHERE id = :id").param("id", purchase).update();

		assertThat(allocations.findByTransactionIds(List.of(refund)).get(refund)).singleElement()
			.satisfies(piece -> assertThat(piece.refundOf()).isNull())
			.satisfies(piece -> assertThat(piece.amount()).isEqualByComparingTo("800.00"));
	}

	private UUID insertPurchase(String amount) {
		return insertPurchase(amount, request(AllocationKind.EXPENSE, diningOut, amount, null));
	}

	private UUID insertPurchase(String amount, AllocationRequest... pieces) {
		Transaction transaction = Transaction.create(account, LocalDate.of(2026, 8, 20), null, "SWIGGY ORDER",
				new BigDecimal(amount), null, null, Map.of());
		transactions.insert(transaction);
		service.classify(transaction.id(), List.of(pieces));
		return transaction.id();
	}

	private UUID insertTransaction(String amount) {
		Transaction transaction = Transaction.create(account, LocalDate.of(2026, 9, 1), null, "UPI-SOMETHING",
				new BigDecimal(amount), null, null, Map.of());
		transactions.insert(transaction);
		return transaction.id();
	}

	private UUID stubCategory(String name, CategoryKind kind) {
		UUID id = jdbc.sql("SELECT id FROM categories WHERE name = :name").param("name", name).query(UUID.class).single();
		given(categories.findCategory(id))
			.willReturn(Optional.of(new Category(id, null, name, kind, Instant.parse("2026-01-01T00:00:00Z"))));
		return id;
	}

	private static AllocationRequest request(AllocationKind kind, UUID categoryId, String amount, String note) {
		return new AllocationRequest(kind, categoryId, new BigDecimal(amount), note, null);
	}

	private static AllocationRequest refund(UUID categoryId, String amount, UUID purchase) {
		return new AllocationRequest(AllocationKind.EXPENSE, categoryId, new BigDecimal(amount), null, purchase);
	}

}
