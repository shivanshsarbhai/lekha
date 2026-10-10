package com.lekha.accounts;

/** The request describes an account that can't exist, e.g. with a blank nickname or a missing type. */
public class InvalidAccountException extends RuntimeException {

	InvalidAccountException(String message) {
		super(message);
	}

}
