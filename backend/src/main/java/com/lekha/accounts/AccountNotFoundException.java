package com.lekha.accounts;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException {

	public AccountNotFoundException(UUID accountId) {
		super("No account with id " + accountId);
	}

}
