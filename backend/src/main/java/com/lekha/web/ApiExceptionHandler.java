package com.lekha.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.lekha.accounts.DuplicateAccountNicknameException;
import com.lekha.statements.AccountNotFoundException;
import com.lekha.statements.StatementFormatException;
import com.lekha.statements.UnsupportedAccountException;

/** Turns the exceptions our features throw on purpose into HTTP responses whose message the user can act on. */
@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler(DuplicateAccountNicknameException.class)
	ProblemDetail conflict(RuntimeException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(AccountNotFoundException.class)
	ProblemDetail notFound(RuntimeException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler({ UnsupportedAccountException.class, StatementFormatException.class })
	ProblemDetail unprocessable(RuntimeException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
	}

}
