package com.lekha.transactions;

import java.util.UUID;

public class TransactionNotFoundException extends RuntimeException {

	TransactionNotFoundException(UUID id) {
		super("No transaction with id " + id);
	}

}
