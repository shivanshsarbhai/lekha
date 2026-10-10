package com.lekha.accounts;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** An account as shown in the account list, with how many transactions deleting it would remove. */
record ListedAccount(UUID id, String nickname, AccountType type, Institution institution, @Nullable String last4,
		Instant createdAt, int transactionCount) {

	ListedAccount(Account account, int transactionCount) {
		this(account.id(), account.nickname(), account.type(), account.institution(), account.last4(),
				account.createdAt(), transactionCount);
	}

}
