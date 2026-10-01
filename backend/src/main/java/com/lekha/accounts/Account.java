package com.lekha.accounts;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * An account as stored in the database: a bank account or a credit card.
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
	}
}
