package com.lekha.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.lekha.TestcontainersConfiguration;

@JdbcTest
@Import({ TestcontainersConfiguration.class, AccountRepository.class })
class AccountRepositoryTest {

	@Autowired
	private AccountRepository repository;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void findAllReturnsEmptyListWhenThereAreNoAccounts() {
		assertThat(repository.findAll()).isEmpty();
	}

	@Test
	void findAllMapsEveryColumnAndOrdersOldestFirst() {
		insertAccount("HDFC Salary", "BANK", "HDFC", "1234", "2026-02-01T10:00:00Z");
		insertAccount("Scapia Card", "CREDIT_CARD", "FEDERAL_BANK", null, "2026-01-01T10:00:00Z");

		List<Account> accounts = repository.findAll();

		assertThat(accounts).extracting(Account::nickname)
				.containsExactly("Scapia Card", "HDFC Salary");

		Account scapia = accounts.get(0);
		assertThat(scapia.id()).isNotNull();
		assertThat(scapia.type()).isEqualTo(AccountType.CREDIT_CARD);
		assertThat(scapia.institution()).isEqualTo(Institution.FEDERAL_BANK);
		assertThat(scapia.last4()).isNull();
		assertThat(scapia.createdAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));

		Account hdfc = accounts.get(1);
		assertThat(hdfc.type()).isEqualTo(AccountType.BANK);
		assertThat(hdfc.institution()).isEqualTo(Institution.HDFC);
		assertThat(hdfc.last4()).isEqualTo("1234");
	}

	private void insertAccount(String nickname, String type, String institution, @Nullable String last4,
			String createdAt) {
		jdbc.sql("""
				INSERT INTO accounts (nickname, type, institution, last4, created_at)
				VALUES (:nickname, :type, :institution, :last4, :createdAt)
				""")
				.param("nickname", nickname)
				.param("type", type)
				.param("institution", institution)
				.param("last4", last4)
				.param("createdAt", OffsetDateTime.parse(createdAt))
				.update();
	}

}
