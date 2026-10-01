package com.lekha.categories;

/** The request describes a category that can't exist, e.g. three levels deep or with the wrong kind. */
public class InvalidCategoryException extends RuntimeException {

	InvalidCategoryException(String message) {
		super(message);
	}

}
