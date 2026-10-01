package com.lekha.categories;

import java.util.UUID;

public class CategoryNotFoundException extends RuntimeException {

	CategoryNotFoundException(UUID id) {
		super("No category with id " + id);
	}

}
