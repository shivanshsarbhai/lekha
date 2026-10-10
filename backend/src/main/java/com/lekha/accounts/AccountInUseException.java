package com.lekha.accounts;

/** The change would contradict transactions already imported into the account. */
public class AccountInUseException extends RuntimeException {

	AccountInUseException(String message) {
		super(message);
	}

}
