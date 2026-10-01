package com.lekha.statements;

import java.util.UUID;

class AccountNotFoundException extends RuntimeException {

	AccountNotFoundException(UUID accountId) {
		super("No account with id " + accountId);
	}

}
