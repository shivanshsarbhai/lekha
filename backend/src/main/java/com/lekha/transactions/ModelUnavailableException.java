package com.lekha.transactions;

/** The language model couldn't answer. The message says what to do, e.g. start Ollama or download the model. */
public class ModelUnavailableException extends RuntimeException {

	public ModelUnavailableException(String message) {
		super(message);
	}

}
