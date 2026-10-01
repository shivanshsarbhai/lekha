package com.lekha.accounts;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
class AccountService {

	private final AccountRepository repository;

	AccountService(AccountRepository repository) {
		this.repository = repository;
	}

	Account createAccount(String nickname, AccountType type, Institution institution, @Nullable String last4) {
		Account account = Account.create(nickname, type, institution, last4);
		try {
			repository.insert(account);
		}
		catch (DuplicateKeyException ex) {
			throw new DuplicateAccountNicknameException(nickname, ex);
		}
		return account;
	}

}
