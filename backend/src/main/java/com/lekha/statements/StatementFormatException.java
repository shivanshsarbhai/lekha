package com.lekha.statements;

/** Thrown when a statement file does not have the layout its parser expects. */
public class StatementFormatException extends RuntimeException {

	StatementFormatException(String message) {
		super(message);
	}

	StatementFormatException(String message, Throwable cause) {
		super(message, cause);
	}

}
