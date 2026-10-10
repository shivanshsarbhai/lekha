package com.lekha.accounts;

import org.jspecify.annotations.Nullable;

/** JSON body of {@code PUT /api/v1/accounts/{id}}: the whole account, so a missing last4 clears it. */
record UpdateAccountRequest(@Nullable String nickname, @Nullable AccountType type, @Nullable Institution institution,
		@Nullable String last4) {
}
