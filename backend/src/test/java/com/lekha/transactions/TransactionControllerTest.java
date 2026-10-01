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
		given(service.list(null, SEP_1, SEP_30)).willReturn(new TransactionList(List.of(swiggy),
				new BigDecimal("0.00"), new BigDecimal("-450.00"), new BigDecimal("-450.00")));

		var response = assertThat(mvc.get().uri("/api/v1/transactions?from=2026-09-01&to=2026-09-30"))
				.hasStatusOk()
				.bodyJson();

		response.extractingPath("$.transactions[0].id").isEqualTo("22222222-2222-2222-2222-222222222222");
		response.extractingPath("$.transactions[0].description").isEqualTo("UPI-SWIGGY");
		response.extractingPath("$.transactions.length()").isEqualTo(1);
		response.hasPath("$.moneyIn").hasPath("$.moneyOut").hasPath("$.net");
	}

	@Test
	void listPassesTheAccountFilterThrough() {
		given(service.list(ACCOUNT_ID, SEP_1, SEP_30)).willReturn(new TransactionList(List.of(),
				new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00")));

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
	void listReturns404ForAnUnknownAccount() {
		given(service.list(ACCOUNT_ID, SEP_1, SEP_30)).willThrow(new AccountNotFoundException(ACCOUNT_ID));

		assertThat(mvc.get().uri("/api/v1/transactions?accountId={id}&from=2026-09-01&to=2026-09-30", ACCOUNT_ID))
				.hasStatus(HttpStatus.NOT_FOUND);
	}

}
