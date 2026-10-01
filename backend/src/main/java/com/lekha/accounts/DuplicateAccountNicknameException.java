package com.lekha.accounts;

/** Thrown when creating an account whose nickname is already used, ignoring case. */
public class DuplicateAccountNicknameException extends RuntimeException {

	DuplicateAccountNicknameException(String nickname, Throwable cause) {
		super("You already have an account called \"" + nickname + "\"", cause);
	}

}
