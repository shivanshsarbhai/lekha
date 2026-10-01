package com.lekha.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccountTest {

	@Test
	void nicknameIsTrimmed() {
		assertThat(accountWith("  HDFC Salary  ", null).nickname()).isEqualTo("HDFC Salary");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "\t\n" })
	void blankNicknameIsRejected(String nickname) {
		assertThatThrownBy(() -> accountWith(nickname, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Nickname must not be blank");
	}

	@Test
	void nicknameOfMaxLengthIsAccepted() {
		String sixty = "a".repeat(AccountConstants.MAX_NICKNAME_LENGTH);

		assertThat(accountWith(sixty, null).nickname()).isEqualTo(sixty);
	}

	@Test
	void nicknameLongerThanMaxLengthIsRejected() {
		String sixtyOne = "a".repeat(AccountConstants.MAX_NICKNAME_LENGTH + 1);

		assertThatThrownBy(() -> accountWith(sixtyOne, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Nickname must be at most 60 characters");
	}

	@Test
	void last4IsTrimmed() {
		assertThat(accountWith("HDFC Salary", " 1234 ").last4()).isEqualTo("1234");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void blankLast4BecomesNull(String last4) {
		assertThat(accountWith("HDFC Salary", last4).last4()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "123", "12345", "12ab", "12 34" })
	void last4ThatIsNotExactlyFourDigitsIsRejected(String last4) {
		assertThatThrownBy(() -> accountWith("HDFC Salary", last4))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Last 4 digits must be exactly 4 digits");
	}

	private static Account accountWith(String nickname, @Nullable String last4) {
		return Account.create(nickname, AccountType.BANK, Institution.HDFC, last4);
	}

}
