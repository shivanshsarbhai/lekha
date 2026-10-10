package com.lekha.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.lekha.accounts.AccountNotFoundException;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

	private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	private static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);

	private static final LocalDate SEP_30 = LocalDate.of(2026, 9, 30);

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	TransactionService service;

	@Test
	void listReturnsTheTransactionsAndTotals() {
		Transaction swiggy = new Transaction(UUID.fromString("22222222-2222-2222-2222-222222222222"), ACCOUNT_ID,
				LocalDate.of(2026, 9, 3), null, "UPI-SWIGGY", new BigDecimal("-450.00"), new BigDecimal("9550.00"),
				PaymentMode.UPI, Map.of(), Instant.parse("2026-10-01T10:00:00Z"));
		Allocation food = new Allocation(UUID.fromString("44444444-4444-4444-4444-444444444444"), swiggy.id(),
				AllocationKind.EXPENSE, null, new BigDecimal("-450.00"), null, null);
		given(service.list(null, SEP_1, SEP_30)).willReturn(new TransactionList(
				List.of(new ListedTransaction(swiggy, List.of(food))), new BigDecimal("0.00"),
				new BigDecimal("-450.00"), new BigDecimal("-450.00"), new BigDecimal("450.00"), new BigDecimal("0.00"),
				new BigDecimal("0.00"), new BigDecimal("0.00"), 0));

		var response = assertThat(mvc.get().uri("/api/v1/transactions?from=2026-09-01&to=2026-09-30"))
				.hasStatusOk()
				.bodyJson();

		response.extractingPath("$.transactions[0].id").isEqualTo("22222222-2222-2222-2222-222222222222");
		response.extractingPath("$.transactions[0].description").isEqualTo("UPI-SWIGGY");
		response.extractingPath("$.transactions[0].allocations[0].kind").isEqualTo("EXPENSE");
		response.doesNotHavePath("$.transactions[0].transaction");
		response.extractingPath("$.transactions.length()").isEqualTo(1);
		response.hasPath("$.moneyIn").hasPath("$.moneyOut").hasPath("$.net");
		response.hasPath("$.spending").hasPath("$.income").hasPath("$.invested").hasPath("$.lent");
		response.extractingPath("$.unclassifiedCount").isEqualTo(0);
	}

	@Test
	void listPassesTheAccountFilterThrough() {
		BigDecimal zero = new BigDecimal("0.00");
		given(service.list(ACCOUNT_ID, SEP_1, SEP_30))
			.willReturn(new TransactionList(List.of(), zero, zero, zero, zero, zero, zero, zero, 0));

		assertThat(mvc.get().uri("/api/v1/transactions?accountId={id}&from=2026-09-01&to=2026-09-30", ACCOUNT_ID))
				.hasStatusOk()
				.bodyJson()
				.extractingPath("$.transactions")
				.asArray()
				.isEmpty();
	}

	@Test
	void listReturns400WhenADateIsMissing() {
		assertThat(mvc.get().uri("/api/v1/transactions?from=2026-09-01")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void listReturns400WhenADateIsNotIso() {
		assertThat(mvc.get().uri("/api/v1/transactions?from=01/09/2026&to=2026-09-30"))
				.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void listReturns400WithTheReasonWhenTheRangeIsInvalid() {
		given(service.list(null, SEP_30, SEP_1)).willThrow(new InvalidDateRangeException("from after to"));

		assertThat(mvc.get().uri("/api/v1/transactions?from=2026-09-30&to=2026-09-01"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("from after to");
	}

	@Test
	void updateReturnsTheEditedTransaction() {
		UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
		Transaction edited = new Transaction(id, ACCOUNT_ID, LocalDate.of(2026, 9, 4), null, "Rent",
				new BigDecimal("-34000.00"), null, null, Map.of("originalAmount", "-35000.00"),
				Instant.parse("2026-10-01T10:00:00Z"));
		given(service.update(id, LocalDate.of(2026, 9, 4), "Rent", new BigDecimal("-34000.00")))
			.willReturn(new ListedTransaction(edited, List.of()));

		var response = assertThat(mvc.put().uri("/api/v1/transactions/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"transactionDate": "2026-09-04", "description": "Rent", "amount": -34000.00}
						"""))
				.hasStatusOk()
				.bodyJson();

		response.extractingPath("$.description").isEqualTo("Rent");
		response.extractingPath("$.transactionDate").isEqualTo("2026-09-04");
		response.extractingPath("$.metadata.originalAmount").isEqualTo("-35000.00");
		response.extractingPath("$.allocations").asArray().isEmpty();
	}

	@Test
	void updateReturns400WithTheReasonForAnInvalidEdit() {
		UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
		given(service.update(id, LocalDate.of(2026, 9, 4), "Rent", new BigDecimal("0")))
			.willThrow(new InvalidTransactionException("Amount must not be zero"));

		assertThat(mvc.put().uri("/api/v1/transactions/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"transactionDate": "2026-09-04", "description": "Rent", "amount": 0}
						"""))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("Amount must not be zero");
	}

	@Test
	void updateReturns404ForAnUnknownTransaction() {
		UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
		given(service.update(id, LocalDate.of(2026, 9, 4), "Rent", new BigDecimal("-1.00")))
			.willThrow(new TransactionNotFoundException(id));

		assertThat(mvc.put().uri("/api/v1/transactions/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"transactionDate": "2026-09-04", "description": "Rent", "amount": -1.00}
						"""))
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void listIncludesRefundDetails() {
		UUID purchaseId = UUID.fromString("33333333-3333-3333-3333-333333333333");
		Transaction refund = new Transaction(UUID.fromString("22222222-2222-2222-2222-222222222222"), ACCOUNT_ID,
				LocalDate.of(2026, 9, 9), null, "MYNTRA REFUND", new BigDecimal("1000.00"), null, PaymentMode.CARD,
				Map.of(), Instant.parse("2026-10-01T10:00:00Z"));
		Allocation piece = new Allocation(UUID.fromString("44444444-4444-4444-4444-444444444444"), refund.id(),
				AllocationKind.EXPENSE, null, new BigDecimal("1000.00"), null, purchaseId);
		BigDecimal zero = new BigDecimal("0.00");
		given(service.list(null, SEP_1, SEP_30)).willReturn(new TransactionList(
				List.of(new ListedTransaction(refund, List.of(piece), zero,
						List.of(new ListedTransaction.RefundedPurchase(purchaseId, SEP_1, "MYNTRA SHOES")), null)),
				zero, zero, zero, zero, zero, zero, zero, 0));

		var response = assertThat(mvc.get().uri("/api/v1/transactions?from=2026-09-01&to=2026-09-30"))
				.hasStatusOk()
				.bodyJson();

		response.extractingPath("$.transactions[0].allocations[0].refundOf").isEqualTo(purchaseId.toString());
		response.extractingPath("$.transactions[0].refundedAmount").isEqualTo(0.0);
		response.extractingPath("$.transactions[0].refundedPurchases[0].description").isEqualTo("MYNTRA SHOES");
		response.extractingPath("$.transactions[0].refundedPurchases[0].transactionDate").isEqualTo("2026-09-01");
	}

	@Test
	void listIncludesTheAiSuggestion() {
		Transaction swiggy = new Transaction(UUID.fromString("22222222-2222-2222-2222-222222222222"), ACCOUNT_ID,
				LocalDate.of(2026, 9, 3), null, "UPI-SWIGGY", new BigDecimal("-450.00"), null, PaymentMode.UPI, Map.of(),
				Instant.parse("2026-10-01T10:00:00Z"));
		UUID orderingIn = UUID.fromString("55555555-5555-5555-5555-555555555555");
		BigDecimal zero = new BigDecimal("0.00");
		given(service.list(null, SEP_1, SEP_30)).willReturn(new TransactionList(
				List.of(new ListedTransaction(swiggy, List.of(), zero, List.of(), new AiSuggestion(swiggy.id(),
						AllocationKind.EXPENSE, orderingIn, "Swiggy delivers food", "qwen2.5:7b"))),
				zero, zero, zero, zero, zero, zero, zero, 1));

		var response = assertThat(mvc.get().uri("/api/v1/transactions?from=2026-09-01&to=2026-09-30"))
				.hasStatusOk()
				.bodyJson();

		response.extractingPath("$.transactions[0].suggestion.kind").isEqualTo("EXPENSE");
		response.extractingPath("$.transactions[0].suggestion.categoryId").isEqualTo(orderingIn.toString());
		response.extractingPath("$.transactions[0].suggestion.reason").isEqualTo("Swiggy delivers food");
		response.extractingPath("$.transactions[0].suggestion.model").isEqualTo("qwen2.5:7b");
		response.doesNotHavePath("$.transactions[0].suggestion.transactionId");
	}

	@Test
	void refundCandidatesPassesTheSearchThrough() {
		UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
		UUID category = UUID.fromString("55555555-5555-5555-5555-555555555555");
		given(service.refundCandidates(id, "myntra")).willReturn(List.of(new RefundCandidate(
				UUID.fromString("33333333-3333-3333-3333-333333333333"), ACCOUNT_ID, SEP_1, "MYNTRA SHOES",
				new BigDecimal("3000.00"), new BigDecimal("2000.00"), List.of(category))));

		var response = assertThat(mvc.get().uri("/api/v1/transactions/{id}/refund-candidates?search=myntra", id))
				.hasStatusOk()
				.bodyJson();

		response.extractingPath("$[0].description").isEqualTo("MYNTRA SHOES");
		response.extractingPath("$[0].refundable").isEqualTo(2000.0);
		response.extractingPath("$[0].categoryIds[0]").isEqualTo(category.toString());
	}

	@Test
	void updateReturns409WhenARefundedPurchasesAmountChanges() {
		UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
		given(service.update(id, LocalDate.of(2026, 9, 4), "Shoes", new BigDecimal("-2500")))
			.willThrow(new RefundConflictException("Unlink its refunds first."));

		assertThat(mvc.put().uri("/api/v1/transactions/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"transactionDate": "2026-09-04", "description": "Shoes", "amount": -2500}
						"""))
				.hasStatus(HttpStatus.CONFLICT)
				.bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("Unlink its refunds first.");
	}

	@Test
	void listReturns404ForAnUnknownAccount() {
		given(service.list(ACCOUNT_ID, SEP_1, SEP_30)).willThrow(new AccountNotFoundException(ACCOUNT_ID));

		assertThat(mvc.get().uri("/api/v1/transactions?accountId={id}&from=2026-09-01&to=2026-09-30", ACCOUNT_ID))
				.hasStatus(HttpStatus.NOT_FOUND);
	}

}
