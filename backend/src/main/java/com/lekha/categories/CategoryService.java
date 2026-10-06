package com.lekha.categories;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {

	private final CategoryRepository repository;

	CategoryService(CategoryRepository repository) {
		this.repository = repository;
	}

	public Optional<Category> findCategory(UUID id) {
		return repository.findById(id);
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

	/**
	 * A top-level category needs a kind. A sub-category takes its parent's kind, and its parent must be top-level so
	 * the tree never grows past two levels.
	 */
	Category create(@Nullable String name, @Nullable CategoryKind kind, @Nullable UUID parentId) {
		CategoryKind resolvedKind;
		if (parentId == null) {
			if (kind == null) {
				throw new InvalidCategoryException("A top-level category needs a kind: EXPENSE, INCOME, INVESTMENT or TRANSFER");
			}
			resolvedKind = kind;
		}
		else {
			Category parent = repository.findById(parentId)
				.orElseThrow(() -> new InvalidCategoryException("No category with id " + parentId + " to add this under"));
			if (!parent.isTopLevel()) {
				throw new InvalidCategoryException("\"" + parent.name() + "\" is already a sub-category, so it can't have its own");
			}
			if (kind != null && kind != parent.kind()) {
				throw new InvalidCategoryException(
						"A sub-category of \"" + parent.name() + "\" must also be " + parent.kind());
			}
			resolvedKind = parent.kind();
		}

		Category category = validated(() -> Category.create(parentId, nonNull(name), resolvedKind));
		saveUnique(category.name(), () -> repository.insert(category));
		return category;
	}

	Category rename(UUID id, @Nullable String name) {
		Category existing = repository.findById(id).orElseThrow(() -> new CategoryNotFoundException(id));
		Category renamed = validated(() -> existing.withName(nonNull(name)));
		saveUnique(renamed.name(), () -> repository.rename(id, renamed.name()));
		return renamed;
	}

	void delete(UUID id) {
		Category category = repository.findById(id).orElseThrow(() -> new CategoryNotFoundException(id));
		if (repository.hasChildren(id)) {
			throw new CategoryInUseException(
					"\"" + category.name() + "\" has sub-categories. Delete or rename those first.");
		}
		try {
			repository.delete(id);
		}
		catch (DataIntegrityViolationException ex) {
			throw new CategoryInUseException(
					"\"" + category.name() + "\" is used by classified transactions. Re-classify them first.");
		}
	}

	/** Turns the record's own validation errors (blank or too-long name) into a 400 rather than a 500. */
	private static Category validated(Supplier<Category> build) {
		try {
			return build.get();
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidCategoryException(ex.getMessage());
		}
	}

	private static void saveUnique(String name, Runnable save) {
		try {
			save.run();
		}
		catch (DuplicateKeyException ex) {
			throw new DuplicateCategoryNameException(name, ex);
		}
	}

	private static String nonNull(@Nullable String name) {
		return name == null ? "" : name;
	}

}
