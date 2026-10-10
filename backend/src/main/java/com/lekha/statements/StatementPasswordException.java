package com.lekha.statements;

/**
 * Thrown when a statement PDF is locked and no password, or the wrong one, was given. {@link #code()} lets the client
 * tell the two apart and ask for the password. The password itself is never part of the message.
 */
public class StatementPasswordException extends RuntimeException {

	private final String code;

	StatementPasswordException(boolean passwordGiven) {
		super(passwordGiven ? "That password didn't open the PDF. Check it and try again."
				: "This PDF is password-protected. Enter the password your bank gave you to open it.");
		this.code = passwordGiven ? "WRONG_PASSWORD" : "PASSWORD_REQUIRED";
	}

	public String code() {
		return code;
	}

}
