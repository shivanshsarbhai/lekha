package com.lekha.categories;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
class CategoryService {

	private final CategoryRepository repository;

	CategoryService(CategoryRepository repository) {
		this.repository = repository;
	}

	/** Every category as a two-level tree: grouped by kind (expenses first), then alphabetical. */
	List<CategoryNode> tree() {
		List<Category> all = repository.findAll();
		Map<UUID, List<Category>> childrenByParent = all.stream()
			.filter(category -> !category.isTopLevel())
			.collect(Collectors.groupingBy(Category::parentId));

		return all.stream()
			.filter(Category::isTopLevel)
			.sorted(Comparator.comparing(Category::kind))
			.map(parent -> new CategoryNode(parent.id(), parent.name(), parent.kind(),
					childrenByParent.getOrDefault(parent.id(), List.of())
						.stream()
						.map(child -> new CategoryNode(child.id(), child.name(), child.kind(), List.of()))
						.toList()))
			.toList();
	}

}
