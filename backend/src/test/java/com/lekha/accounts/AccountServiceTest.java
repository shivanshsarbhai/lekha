package com.lekha.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

import com.lekha.TestcontainersConfiguration;

@JdbcTest
@Import({ TestcontainersConfiguration.class, AccountRepository.class, AccountService.class })
class AccountServiceTest {

	@Autowired
	private AccountService service;

	@Autowired
	private AccountRepository repository;

	@Test
	void createAccountSavesAndReturnsTheAccount() {
		Account created = service.createAccount("HDFC Salary", AccountType.BANK, Institution.HDFC, "1234");

		assertThat(repository.findById(created.id())).contains(created);
	}

	@Test
	void createAccountRejectsDuplicateNicknameWithAFriendlyMessage() {
		service.createAccount("HDFC Salary", AccountType.BANK, Institution.HDFC, "1234");

		assertThatThrownBy(() -> service.createAccount("hdfc salary", AccountType.CREDIT_CARD, Institution.SBI, null))
				.isInstanceOf(DuplicateAccountNicknameException.class)
				.hasMessage("You already have an account called \"hdfc salary\"")
				.hasCauseInstanceOf(DuplicateKeyException.class);
	}

}
