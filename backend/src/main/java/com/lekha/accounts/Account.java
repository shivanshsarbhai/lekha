package com.lekha.accounts;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A bank account or a credit card.
 */
public record Account(
		UUID id,
		String nickname,
		AccountType type,
		Institution institution,
		@Nullable String last4,
		Instant createdAt) {

	public Account {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(nickname, "nickname");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(institution, "institution");
		Objects.requireNonNull(createdAt, "createdAt");
		createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
	}

	/** Creates an account that has not been saved yet, with a freshly generated id. */
	public static Account create(String nickname, AccountType type, Institution institution, @Nullable String last4) {
		return new Account(UUID.randomUUID(), nickname, type, institution, last4, Instant.now());
	}
}
