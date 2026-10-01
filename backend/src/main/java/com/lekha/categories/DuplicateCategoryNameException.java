package com.lekha.categories;

public class DuplicateCategoryNameException extends RuntimeException {

	DuplicateCategoryNameException(String name, Throwable cause) {
		super("There is already a category called \"" + name + "\" here", cause);
	}

}
