package com.lekha.statements;

import com.lekha.accounts.Account;

class UnsupportedAccountException extends RuntimeException {

	UnsupportedAccountException(Account account) {
		super("Statements for " + account.institution() + " " + account.type() + " accounts can't be imported yet");
	}

}
