package com.lekha.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.lekha.accounts.AccountNotFoundException;
import com.lekha.accounts.DuplicateAccountNicknameException;
import com.lekha.categories.CategoryInUseException;
import com.lekha.categories.CategoryNotFoundException;
import com.lekha.categories.DuplicateCategoryNameException;
import com.lekha.categories.InvalidCategoryException;
import com.lekha.statements.StatementFormatException;
import com.lekha.statements.UnsupportedAccountException;
import com.lekha.transactions.InvalidDateRangeException;

/** Turns the exceptions our features throw on purpose into HTTP responses whose message the user can act on. */
@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler({ InvalidDateRangeException.class, InvalidCategoryException.class })
	ProblemDetail badRequest(RuntimeException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler({ DuplicateAccountNicknameException.class, DuplicateCategoryNameException.class,
			CategoryInUseException.class })
	ProblemDetail conflict(RuntimeException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler({ AccountNotFoundException.class, CategoryNotFoundException.class })
	ProblemDetail notFound(RuntimeException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler({ UnsupportedAccountException.class, StatementFormatException.class })
	ProblemDetail unprocessable(RuntimeException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
	}

}
