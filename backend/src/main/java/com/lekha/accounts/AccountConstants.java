package com.lekha.accounts;

import java.util.regex.Pattern;

/** Limits for account fields. Keep them in sync with the accounts table in V1. */
final class AccountConstants {

	static final int MAX_NICKNAME_LENGTH = 60;

	static final Pattern LAST4_PATTERN = Pattern.compile("[0-9]{4}");

	private AccountConstants() {
	}

}
