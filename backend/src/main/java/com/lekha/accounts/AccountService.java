package com.lekha.accounts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

	private final AccountRepository repository;

	AccountService(AccountRepository repository) {
		this.repository = repository;
	}

	List<Account> listAccounts() {
		return repository.findAll();
	}

	public Optional<Account> findAccount(UUID id) {
		return repository.findById(id);
	}

	/** For callers where a missing account is an error, not an expected answer. */
	public Account getAccount(UUID id) {
		return findAccount(id).orElseThrow(() -> new AccountNotFoundException(id));
	}

	Account createAccount(String nickname, AccountType type, Institution institution, @Nullable String last4) {
		Account account = Account.create(nickname, type, institution, last4);
		try {
			repository.insert(account);
		}
		catch (DuplicateKeyException ex) {
			throw new DuplicateAccountNicknameException(account.nickname(), ex);
		}
		return account;
	}

}
