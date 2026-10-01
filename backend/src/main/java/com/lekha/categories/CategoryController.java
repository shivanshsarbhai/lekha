package com.lekha.categories;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
class CategoryController {

	private final CategoryService service;

	CategoryController(CategoryService service) {
		this.service = service;
	}

	@GetMapping
	List<CategoryNode> list() {
		return service.tree();
	}

}
