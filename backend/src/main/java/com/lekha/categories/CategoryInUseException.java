package com.lekha.categories;

/** The category can't be deleted because something still depends on it. */
public class CategoryInUseException extends RuntimeException {

	CategoryInUseException(String message) {
		super(message);
	}

}
