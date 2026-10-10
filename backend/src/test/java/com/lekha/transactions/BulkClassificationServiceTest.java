package com.lekha.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lekha.TestcontainersConfiguration;
import com.lekha.categories.Category;
import com.lekha.categories.CategoryKind;
import com.lekha.categories.CategoryService;

/**
 * Runs outside a test transaction so a failed batch really rolls back, which is what these tests check. Rows persist
 * between tests, so they are deleted after each one.
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ TestcontainersConfiguration.class, TransactionRepository.class, AllocationRepository.class,
		ClassificationService.class })
class BulkClassificationServiceTest {

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

	@BeforeEach
	void setUp() {
		account = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO accounts (id, nickname, type, institution, last4, created_at)
				VALUES (:id, 'HDFC Salary', 'BANK', 'HDFC', NULL, now())
				""").param("id", account).update();
		diningOut = stubCategory("Dining out", CategoryKind.EXPENSE);
		salary = stubCategory("Salary", CategoryKind.INCOME);
	}

	@AfterEach
	void deleteEverything() {
		jdbc.sql("DELETE FROM transactions").update();
		jdbc.sql("DELETE FROM accounts").update();
	}

	@Test
	void givesEachTransactionOnePieceForItsWholeAmount() {
		UUID swiggy = insert("SWIGGY", "-450.00");
		UUID zomato = insert("ZOMATO", "-320.50");
		UUID cashback = insert("CASHBACK", "25.00");

		Map<UUID, List<Allocation>> saved = service
			.classifyAll(new BulkClassificationRequest(List.of(swiggy, zomato, cashback), AllocationKind.EXPENSE,
					diningOut, "Weekend food"));

		assertThat(saved).containsOnlyKeys(swiggy, zomato, cashback);
		assertThat(saved.keySet()).containsExactly(swiggy, zomato, cashback);
		assertThat(saved.get(zomato)).singleElement().satisfies(piece -> {
			assertThat(piece.kind()).isEqualTo(AllocationKind.EXPENSE);
			assertThat(piece.categoryId()).isEqualTo(diningOut);
			assertThat(piece.amount()).isEqualByComparingTo("-320.50");
			assertThat(piece.note()).isEqualTo("Weekend food");
		});
		assertThat(saved.get(cashback)).singleElement()
			.satisfies(piece -> assertThat(piece.amount()).isEqualByComparingTo("25.00"));
		assertThat(allocations.findByTransactionIds(List.of(swiggy, zomato, cashback))).isEqualTo(saved);
	}

	@Test
	void replacesWhatEachTransactionHadBefore() {
		UUID dinner = insert("DINNER", "-2000.00");
		service.classify(dinner, List.of(request(AllocationKind.EXPENSE, diningOut, "-500.00"),
				request(AllocationKind.LENT, null, "-1500.00")));

		service.classifyAll(new BulkClassificationRequest(List.of(dinner), AllocationKind.TRANSFER, null, null));

		assertThat(allocations.findByTransactionIds(List.of(dinner)).get(dinner)).singleElement()
			.satisfies(piece -> assertThat(piece.kind()).isEqualTo(AllocationKind.TRANSFER));
	}

	@Test
	void aCategoryOfTheWrongKindRejectsTheBatchWithItsOwnMessage() {
		UUID swiggy = insert("SWIGGY", "-450.00");

		assertThatThrownBy(() -> service.classifyAll(
				new BulkClassificationRequest(List.of(swiggy), AllocationKind.EXPENSE, salary, null)))
			.isInstanceOf(InvalidAllocationException.class)
			.hasMessage("\"Salary\" is an INCOME category, so it can't be used for an EXPENSE allocation");
	}

	@Test
	void oneProtectedPurchaseSavesNoneOfThem() {
		UUID swiggy = insert("SWIGGY", "-450.00");
		UUID shoes = insert("SHOES", "-3000.00");
		service.classify(shoes, List.of(request(AllocationKind.EXPENSE, diningOut, "-3000.00")));
		UUID refund = insert("SHOES REFUND", "1000.00");
		service.classify(refund, List.of(new AllocationRequest(AllocationKind.EXPENSE, diningOut,
				new BigDecimal("1000.00"), null, shoes)));

		assertThatThrownBy(() -> service.classifyAll(
				new BulkClassificationRequest(List.of(swiggy, shoes), AllocationKind.TRANSFER, null, null)))
			.isInstanceOf(RefundConflictException.class)
			.hasMessageStartingWith("\"SHOES\" on 2026-09-01: 1000.00 of this purchase was refunded by");

		assertThat(allocations.findByTransactionIds(List.of(swiggy))).isEmpty();
		assertThat(allocations.findByTransactionIds(List.of(shoes)).get(shoes)).extracting(Allocation::kind)
			.containsExactly(AllocationKind.EXPENSE);
	}

	@Test
	void anUnknownTransactionRejectsTheBatch() {
		UUID swiggy = insert("SWIGGY", "-450.00");
		UUID unknown = UUID.randomUUID();

		assertThatThrownBy(() -> service.classifyAll(
				new BulkClassificationRequest(List.of(swiggy, unknown), AllocationKind.EXPENSE, diningOut, null)))
			.isInstanceOf(TransactionNotFoundException.class)
			.hasMessage("No transaction with id " + unknown);
		assertThat(allocations.findByTransactionIds(List.of(swiggy))).isEmpty();
	}

	@Test
	void rejectsRequestsItCannotApply() {
		UUID swiggy = insert("SWIGGY", "-450.00");

		assertRejected(new BulkClassificationRequest(null, AllocationKind.EXPENSE, null, null),
				"Choose at least one transaction to classify");
		assertRejected(new BulkClassificationRequest(List.of(), AllocationKind.EXPENSE, null, null),
				"Choose at least one transaction to classify");
		assertRejected(new BulkClassificationRequest(List.of(swiggy), null, null, null),
				"Choose a kind to classify the transactions as");
		assertRejected(new BulkClassificationRequest(List.of(swiggy, swiggy), AllocationKind.EXPENSE, null, null),
				"Each transaction can only be listed once");
		assertRejected(new BulkClassificationRequest(Collections.singletonList(null), AllocationKind.EXPENSE, null,
				null), "Transaction ids can't be empty");
		List<UUID> tooMany = new ArrayList<>();
		for (int i = 0; i < 501; i++) {
			tooMany.add(UUID.randomUUID());
		}
		assertRejected(new BulkClassificationRequest(tooMany, AllocationKind.EXPENSE, null, null),
				"At most 500 transactions can be classified at once, not 501");
	}

	@Test
	void aChoiceThatCanNeverWorkIsNotBlamedOnATransaction() {
		UUID swiggy = insert("SWIGGY", "-450.00");

		assertThatThrownBy(() -> service.classifyAll(
				new BulkClassificationRequest(List.of(swiggy), AllocationKind.LENT, diningOut, null)))
			.isInstanceOf(InvalidAllocationException.class)
			.hasMessage("LENT allocations can't have a category");
	}

	private void assertRejected(BulkClassificationRequest request, String message) {
		assertThatThrownBy(() -> service.classifyAll(request)).isInstanceOf(InvalidAllocationException.class)
			.hasMessage(message);
	}

	private UUID insert(String description, String amount) {
		Transaction transaction = Transaction.create(account, LocalDate.of(2026, 9, 1), null, description,
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

	private static AllocationRequest request(AllocationKind kind, UUID categoryId, String amount) {
		return new AllocationRequest(kind, categoryId, new BigDecimal(amount), null, null);
	}

}
