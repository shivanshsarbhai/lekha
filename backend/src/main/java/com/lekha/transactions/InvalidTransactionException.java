package com.lekha.transactions;

/** The edit describes a transaction that can't exist, e.g. with a blank description or a zero amount. */
public class InvalidTransactionException extends RuntimeException {

	InvalidTransactionException(String message) {
		super(message);
	}

}
