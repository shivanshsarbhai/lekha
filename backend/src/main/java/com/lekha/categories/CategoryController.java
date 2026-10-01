package com.lekha.categories;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

	@PostMapping
	ResponseEntity<Category> create(@RequestBody CreateCategoryRequest request) {
		Category category = service.create(request.name(), request.kind(), request.parentId());
		return ResponseEntity.created(URI.create("/api/v1/categories/" + category.id())).body(category);
	}

	@PatchMapping("/{id}")
	Category rename(@PathVariable UUID id, @RequestBody RenameCategoryRequest request) {
		return service.rename(id, request.name());
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

}
