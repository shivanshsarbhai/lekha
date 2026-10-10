package com.lekha.statements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

import com.lekha.accounts.Account;
import com.lekha.accounts.AccountNotFoundException;
import com.lekha.accounts.AccountType;
import com.lekha.accounts.Institution;
import com.lekha.transactions.PaymentMode;
import com.lekha.transactions.Transaction;

@WebMvcTest(StatementController.class)
class StatementControllerTest {

	private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	private static final byte[] PDF = "pretend this is a PDF".getBytes();

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	StatementImportService service;

	@Test
	void uploadReturnsTheImportedTransactionsAndTheSkippedCount() {
		Transaction swiggy = new Transaction(UUID.fromString("22222222-2222-2222-2222-222222222222"), ACCOUNT_ID,
				LocalDate.of(2026, 9, 3), null, "UPI-SWIGGY", new BigDecimal("-450.00"), new BigDecimal("9550.00"),
				PaymentMode.UPI, Map.of("reference", "0000111122223333"), Instant.parse("2026-10-01T10:00:00Z"));
		given(service.importStatement(eq(ACCOUNT_ID), any(), isNull())).willReturn(new StatementImport(List.of(swiggy), 3));

		var response = assertThat(upload(ACCOUNT_ID)).hasStatusOk().bodyJson();

		response.extractingPath("$.skipped").isEqualTo(3);
		response.extractingPath("$.imported[0].id").isEqualTo("22222222-2222-2222-2222-222222222222");
		response.extractingPath("$.imported[0].transactionDate").isEqualTo("2026-09-03");
		response.extractingPath("$.imported[0].description").isEqualTo("UPI-SWIGGY");
		response.extractingPath("$.imported[0].paymentMode").isEqualTo("UPI");
		response.extractingPath("$.imported[0].metadata.reference").isEqualTo("0000111122223333");
	}

	@Test
	void uploadReturns404ForAnUnknownAccount() {
		given(service.importStatement(eq(ACCOUNT_ID), any(), isNull())).willThrow(new AccountNotFoundException(ACCOUNT_ID));

		assertThat(upload(ACCOUNT_ID))
				.hasStatus(HttpStatus.NOT_FOUND)
				.bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("No account with id " + ACCOUNT_ID);
	}

	@Test
	void uploadReturns422WhenTheFileIsNotAValidStatement() {
		given(service.importStatement(eq(ACCOUNT_ID), any(), isNull()))
			.willThrow(new StatementFormatException("Could not read the file as a PDF"));

		assertThat(upload(ACCOUNT_ID))
				.hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
				.bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("Could not read the file as a PDF");
	}

	@Test
	void uploadReturns422WhenTheAccountTypeIsNotSupported() {
		Account federal = Account.create("Federal Savings", AccountType.BANK, Institution.FEDERAL_BANK, null);
		given(service.importStatement(eq(ACCOUNT_ID), any(), isNull())).willThrow(new UnsupportedAccountException(federal));

		assertThat(upload(ACCOUNT_ID))
				.hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
				.bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("Statements for FEDERAL_BANK BANK accounts can't be imported yet");
	}

	@Test
	void uploadPassesThePasswordThrough() {
		given(service.importStatement(eq(ACCOUNT_ID), eq(PDF), eq("TEST0101")))
			.willReturn(new StatementImport(List.of(), 0));

		assertThat(upload(ACCOUNT_ID).param("password", "TEST0101")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.skipped")
			.isEqualTo(0);
	}

	@Test
	void uploadReturns422WithACodeForALockedPdf() {
		given(service.importStatement(eq(ACCOUNT_ID), any(), isNull()))
			.willThrow(new StatementPasswordException(false));

		var response = assertThat(upload(ACCOUNT_ID)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson();

		response.extractingPath("$.code").isEqualTo("PASSWORD_REQUIRED");
		response.extractingPath("$.detail")
			.isEqualTo("This PDF is password-protected. Enter the password your bank gave you to open it.");
	}

	@Test
	void uploadReturns422WithACodeForAWrongPassword() {
		given(service.importStatement(eq(ACCOUNT_ID), any(), eq("WRONG999")))
			.willThrow(new StatementPasswordException(true));

		assertThat(upload(ACCOUNT_ID).param("password", "WRONG999")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("WRONG_PASSWORD");
	}

	@Test
	void uploadReturns400WhenNoFileIsSent() {
		assertThat(mvc.post().uri("/api/v1/accounts/{id}/statements", ACCOUNT_ID).multipart())
				.hasStatus(HttpStatus.BAD_REQUEST);
	}

	private MockMvcTester.MockMultipartMvcRequestBuilder upload(UUID accountId) {
		return mvc.post().uri("/api/v1/accounts/{id}/statements", accountId).multipart().file("file", PDF);
	}

}
