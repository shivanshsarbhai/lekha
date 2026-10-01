package com.lekha.accounts;

import org.jspecify.annotations.Nullable;

/** JSON body of {@code POST /api/v1/accounts}. */
record CreateAccountRequest(String nickname, AccountType type, Institution institution, @Nullable String last4) {
}
