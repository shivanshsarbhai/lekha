package com.lekha.transactions;

import java.util.Map;

import org.jspecify.annotations.Nullable;

/** A language model that answers in JSON. The only implementation talks to Ollama; tests use a fake. */
interface LanguageModel {

	/** Whether the model can answer right now, and if not, what the user should do about it. */
	record Status(boolean available, @Nullable String problem) {
	}

	/** The model's name, saved with each suggestion. */
	String name();

	Status status();

	/**
	 * Sends one conversation and returns the model's reply, which follows {@code schema}.
	 *
	 * @throws ModelUnavailableException when the model can't be reached or refuses the request
	 */
	String chat(String system, String user, Map<String, Object> schema);

}
