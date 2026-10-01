package com.lekha.accounts;

import static com.lekha.accounts.AccountConstants.LAST4_PATTERN;
import static com.lekha.accounts.AccountConstants.MAX_NICKNAME_LENGTH;

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

		nickname = nickname.strip();
		if (nickname.isEmpty()) {
			throw new IllegalArgumentException("Nickname must not be blank");
		}
		if (nickname.length() > MAX_NICKNAME_LENGTH) {
			throw new IllegalArgumentException("Nickname must be at most " + MAX_NICKNAME_LENGTH + " characters");
		}

		if (last4 != null) {
			last4 = last4.strip();
			if (last4.isEmpty()) {
				last4 = null;
			}
			else if (!LAST4_PATTERN.matcher(last4).matches()) {
				throw new IllegalArgumentException("Last 4 digits must be exactly 4 digits");
			}
		}

		createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
	}

	/** Creates an account that has not been saved yet, with a freshly generated id. */
	public static Account create(String nickname, AccountType type, Institution institution, @Nullable String last4) {
		return new Account(UUID.randomUUID(), nickname, type, institution, last4, Instant.now());
	}
}
