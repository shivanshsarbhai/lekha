package com.lekha.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	AccountService service;

	@Test
	void listAccountsReturnsAccountsAsJson() {
		Account hdfc = new Account(UUID.fromString("11111111-1111-1111-1111-111111111111"), "HDFC Salary",
				AccountType.BANK, Institution.HDFC, "1234", Instant.parse("2026-01-01T10:00:00Z"));
		Account scapia = new Account(UUID.fromString("22222222-2222-2222-2222-222222222222"), "Scapia Card",
				AccountType.CREDIT_CARD, Institution.FEDERAL_BANK, null, Instant.parse("2026-02-01T10:00:00Z"));
		given(service.listAccounts()).willReturn(List.of(hdfc, scapia));

		assertThat(mvc.get().uri("/api/v1/accounts"))
				.hasStatusOk()
				.bodyJson()
				.isStrictlyEqualTo("""
						[
						  {
						    "id": "11111111-1111-1111-1111-111111111111",
						    "nickname": "HDFC Salary",
						    "type": "BANK",
						    "institution": "HDFC",
						    "last4": "1234",
						    "createdAt": "2026-01-01T10:00:00Z"
						  },
						  {
						    "id": "22222222-2222-2222-2222-222222222222",
						    "nickname": "Scapia Card",
						    "type": "CREDIT_CARD",
						    "institution": "FEDERAL_BANK",
						    "last4": null,
						    "createdAt": "2026-02-01T10:00:00Z"
						  }
						]
						""");
	}

	@Test
	void listAccountsReturnsEmptyArrayWhenThereAreNoAccounts() {
		given(service.listAccounts()).willReturn(List.of());

		assertThat(mvc.get().uri("/api/v1/accounts"))
				.hasStatusOk()
				.bodyJson()
				.isStrictlyEqualTo("[]");
	}

}
