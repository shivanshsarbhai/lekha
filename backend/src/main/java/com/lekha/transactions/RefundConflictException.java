package com.lekha.transactions;

/** The change would leave a purchase refunded for more than was spent on it, or in a category its refunds don't use. */
public class RefundConflictException extends RuntimeException {

	RefundConflictException(String message) {
		super(message);
	}

}
