package com.lekha.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
		given(service.listAccounts()).willReturn(List.of(new ListedAccount(hdfc, 42), new ListedAccount(scapia, 0)));

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
						    "createdAt": "2026-01-01T10:00:00Z",
						    "transactionCount": 42
						  },
						  {
						    "id": "22222222-2222-2222-2222-222222222222",
						    "nickname": "Scapia Card",
						    "type": "CREDIT_CARD",
						    "institution": "FEDERAL_BANK",
						    "last4": null,
						    "createdAt": "2026-02-01T10:00:00Z",
						    "transactionCount": 0
						  }
						]
						""");
	}

	@Test
	void updateAccountReturnsTheUpdatedAccount() {
		UUID id = UUID.fromString("66666666-6666-6666-6666-666666666666");
		Account updated = new Account(id, "Regalia Gold", AccountType.CREDIT_CARD, Institution.HDFC, null,
				Instant.parse("2026-01-01T10:00:00Z"));
		given(service.updateAccount(id, "Regalia Gold", AccountType.CREDIT_CARD, Institution.HDFC, null))
			.willReturn(updated);

		assertThat(mvc.put().uri("/api/v1/accounts/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nickname": "Regalia Gold", "type": "CREDIT_CARD", "institution": "HDFC", "last4": null}
						"""))
				.hasStatusOk()
				.bodyJson()
				.extractingPath("$.nickname")
				.isEqualTo("Regalia Gold");
	}

	@Test
	void updateAccountReturns409WhenTheAccountAlreadyHasTransactions() {
		UUID id = UUID.fromString("66666666-6666-6666-6666-666666666666");
		given(service.updateAccount(id, "HDFC Salary", AccountType.CREDIT_CARD, Institution.HDFC, null))
			.willThrow(new AccountInUseException("\"HDFC Salary\" already has imported transactions"));

		assertThat(mvc.put().uri("/api/v1/accounts/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nickname": "HDFC Salary", "type": "CREDIT_CARD", "institution": "HDFC", "last4": null}
						"""))
				.hasStatus(HttpStatus.CONFLICT)
				.bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("\"HDFC Salary\" already has imported transactions");
	}

	@Test
	void updateAccountReturns400ForAnInvalidAccount() {
		UUID id = UUID.fromString("66666666-6666-6666-6666-666666666666");
		given(service.updateAccount(id, " ", AccountType.BANK, Institution.HDFC, null))
			.willThrow(new InvalidAccountException("Nickname must not be blank"));

		assertThat(mvc.put().uri("/api/v1/accounts/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nickname": " ", "type": "BANK", "institution": "HDFC", "last4": null}
						"""))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("Nickname must not be blank");
	}

	@Test
	void deleteAccountReturns204() {
		UUID id = UUID.fromString("77777777-7777-7777-7777-777777777777");

		assertThat(mvc.delete().uri("/api/v1/accounts/{id}", id)).hasStatus(HttpStatus.NO_CONTENT);
		then(service).should().deleteAccount(id);
	}

	@Test
	void deleteAccountReturns404WhenItDoesNotExist() {
		UUID id = UUID.fromString("77777777-7777-7777-7777-777777777777");
		willThrow(new AccountNotFoundException(id)).given(service).deleteAccount(id);

		assertThat(mvc.delete().uri("/api/v1/accounts/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void createAccountReturns201WithLocationAndTheCreatedAccount() {
		Account created = new Account(UUID.fromString("33333333-3333-3333-3333-333333333333"), "SBI Savings",
				AccountType.BANK, Institution.SBI, "9876", Instant.parse("2026-03-01T10:00:00Z"));
		given(service.createAccount("SBI Savings", AccountType.BANK, Institution.SBI, "9876")).willReturn(created);

		assertThat(mvc.post().uri("/api/v1/accounts")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nickname": "SBI Savings", "type": "BANK", "institution": "SBI", "last4": "9876"}
						"""))
				.hasStatus(HttpStatus.CREATED)
				.hasHeader("Location", "/api/v1/accounts/33333333-3333-3333-3333-333333333333")
				.bodyJson()
				.isStrictlyEqualTo("""
						{
						  "id": "33333333-3333-3333-3333-333333333333",
						  "nickname": "SBI Savings",
						  "type": "BANK",
						  "institution": "SBI",
						  "last4": "9876",
						  "createdAt": "2026-03-01T10:00:00Z"
						}
						""");
	}

	@Test
	void createAccountReturns409WhenTheNicknameIsTaken() {
		given(service.createAccount("HDFC Salary", AccountType.BANK, Institution.HDFC, null))
			.willThrow(new DuplicateAccountNicknameException("HDFC Salary", new RuntimeException("duplicate key")));

		assertThat(mvc.post().uri("/api/v1/accounts")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nickname": "HDFC Salary", "type": "BANK", "institution": "HDFC", "last4": null}
						"""))
				.hasStatus(HttpStatus.CONFLICT)
				.bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("You already have an account called \"HDFC Salary\"");
	}

	@Test
	void createAccountRejectsUnknownInstitutionWith400() {
		assertThat(mvc.post().uri("/api/v1/accounts")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nickname": "Paytm Wallet", "type": "BANK", "institution": "PAYTM", "last4": null}
						"""))
				.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void getAccountReturnsTheAccountWhenItExists() {
		UUID id = UUID.fromString("44444444-4444-4444-4444-444444444444");
		Account hdfc = new Account(id, "HDFC Salary", AccountType.BANK, Institution.HDFC, "1234",
				Instant.parse("2026-01-01T10:00:00Z"));
		given(service.findAccount(id)).willReturn(Optional.of(hdfc));

		assertThat(mvc.get().uri("/api/v1/accounts/{id}", id))
				.hasStatusOk()
				.bodyJson()
				.isStrictlyEqualTo("""
						{
						  "id": "44444444-4444-4444-4444-444444444444",
						  "nickname": "HDFC Salary",
						  "type": "BANK",
						  "institution": "HDFC",
						  "last4": "1234",
						  "createdAt": "2026-01-01T10:00:00Z"
						}
						""");
	}

	@Test
	void getAccountReturns404WhenItDoesNotExist() {
		UUID id = UUID.fromString("55555555-5555-5555-5555-555555555555");
		given(service.findAccount(id)).willReturn(Optional.empty());

		assertThat(mvc.get().uri("/api/v1/accounts/{id}", id))
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void getAccountReturns400WhenIdIsNotAUuid() {
		assertThat(mvc.get().uri("/api/v1/accounts/not-a-uuid"))
				.hasStatus(HttpStatus.BAD_REQUEST);
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
