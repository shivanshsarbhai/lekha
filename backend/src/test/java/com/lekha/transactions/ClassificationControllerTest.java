package com.lekha.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
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

/** TransactionController is loaded too, to check its PUT /{id} doesn't catch the bulk PUT /allocations. */
@WebMvcTest({ ClassificationController.class, TransactionController.class })
class ClassificationControllerTest {

	private static final UUID TRANSACTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

	private static final UUID DINING_OUT = UUID.fromString("33333333-3333-3333-3333-333333333333");

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	ClassificationService service;

	@MockitoBean
	TransactionService transactions;

	@Test
	void putPassesTheRowsThroughAndReturnsTheSavedSplit() {
		List<AllocationRequest> requests = List.of(
				new AllocationRequest(AllocationKind.EXPENSE, DINING_OUT, new BigDecimal("-500.00"), "My share", null),
				new AllocationRequest(AllocationKind.LENT, null, new BigDecimal("-1500.00"), null, null));
		Allocation food = new Allocation(UUID.fromString("44444444-4444-4444-4444-444444444444"), TRANSACTION_ID,
				AllocationKind.EXPENSE, DINING_OUT, new BigDecimal("-500.00"), "My share", null);
		given(service.classify(TRANSACTION_ID, requests)).willReturn(List.of(food));

		var response = assertThat(put("""
				[
				  { "kind": "EXPENSE", "categoryId": "33333333-3333-3333-3333-333333333333",
				    "amount": -500.00, "note": "My share" },
				  { "kind": "LENT", "amount": -1500.00 }
				]
				""")).hasStatusOk().bodyJson();

		response.extractingPath("$[0].id").isEqualTo("44444444-4444-4444-4444-444444444444");
		response.extractingPath("$[0].kind").isEqualTo("EXPENSE");
		response.extractingPath("$[0].categoryId").isEqualTo("33333333-3333-3333-3333-333333333333");
		response.extractingPath("$[0].amount").isEqualTo(-500.0);
		response.extractingPath("$[0].note").isEqualTo("My share");
	}

	@Test
	void unknownTransactionIs404() {
		given(service.classify(eq(TRANSACTION_ID), any())).willThrow(new TransactionNotFoundException(TRANSACTION_ID));

		assertThat(put("[]")).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("No transaction with id " + TRANSACTION_ID);
	}

	@Test
	void invalidSplitIs400WithTheReason() {
		given(service.classify(eq(TRANSACTION_ID), any()))
			.willThrow(new InvalidAllocationException("Allocations add up to -1800.00 but the transaction is -2000.00"));

		assertThat(put("[]")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Allocations add up to -1800.00 but the transaction is -2000.00");
	}

	@Test
	void unknownKindIs400() {
		assertThat(put("""
				[{ "kind": "SPLURGE", "amount": -500.00 }]
				""")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void bulkPutPassesTheRequestThroughAndReturnsPiecesById() {
		UUID other = UUID.fromString("55555555-5555-5555-5555-555555555555");
		BulkClassificationRequest request = new BulkClassificationRequest(List.of(TRANSACTION_ID, other),
				AllocationKind.EXPENSE, DINING_OUT, null);
		Allocation piece = new Allocation(UUID.fromString("44444444-4444-4444-4444-444444444444"), TRANSACTION_ID,
				AllocationKind.EXPENSE, DINING_OUT, new BigDecimal("-450.00"), null, null);
		Allocation otherPiece = new Allocation(UUID.fromString("66666666-6666-6666-6666-666666666666"), other,
				AllocationKind.EXPENSE, DINING_OUT, new BigDecimal("-320.00"), null, null);
		given(service.classifyAll(request))
			.willReturn(new LinkedHashMap<>(Map.of(TRANSACTION_ID, List.of(piece), other, List.of(otherPiece))));

		var response = assertThat(bulkPut("""
				{ "transactionIds": ["22222222-2222-2222-2222-222222222222", "55555555-5555-5555-5555-555555555555"],
				  "kind": "EXPENSE", "categoryId": "33333333-3333-3333-3333-333333333333" }
				""")).hasStatusOk().bodyJson();

		response.extractingPath("$['22222222-2222-2222-2222-222222222222'][0].amount").isEqualTo(-450.0);
		response.extractingPath("$['55555555-5555-5555-5555-555555555555'][0].categoryId")
			.isEqualTo("33333333-3333-3333-3333-333333333333");
	}

	@Test
	void bulkConflictIs409WithTheReason() {
		given(service.classifyAll(any())).willThrow(new RefundConflictException("\"SHOES\" on 2026-09-01: refunded"));

		assertThat(bulkPut("""
				{ "transactionIds": ["22222222-2222-2222-2222-222222222222"], "kind": "TRANSFER" }
				""")).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("\"SHOES\" on 2026-09-01: refunded");
	}

	private MockMvcTester.MockMvcRequestBuilder bulkPut(String body) {
		return mvc.put().uri("/api/v1/transactions/allocations").contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private MockMvcTester.MockMvcRequestBuilder put(String body) {
		return mvc.put()
			.uri("/api/v1/transactions/{id}/allocations", TRANSACTION_ID)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

}
