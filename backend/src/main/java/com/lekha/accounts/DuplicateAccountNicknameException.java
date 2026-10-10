package com.lekha.accounts;

/** Thrown when saving an account whose nickname another account already uses, ignoring case. */
public class DuplicateAccountNicknameException extends RuntimeException {

	DuplicateAccountNicknameException(String nickname, Throwable cause) {
		super("You already have an account called \"" + nickname + "\"", cause);
	}

}
