package com.lekha.categories;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** A category, or a sub-category when {@code parentId} is set. Sub-categories always share their parent's kind. */
public record Category(UUID id, @Nullable UUID parentId, String name, CategoryKind kind, Instant createdAt) {

	static final int MAX_NAME_LENGTH = 40;

	public Category {
		requireNonNull(id, "id");
		requireNonNull(name, "name");
		requireNonNull(kind, "kind");
		requireNonNull(createdAt, "createdAt");

		name = name.strip();
		if (name.isEmpty()) {
			throw new IllegalArgumentException("Category name must not be blank");
		}
		if (name.length() > MAX_NAME_LENGTH) {
			throw new IllegalArgumentException("Category name must be at most " + MAX_NAME_LENGTH + " characters");
		}
		createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
	}

	static Category create(@Nullable UUID parentId, String name, CategoryKind kind) {
		return new Category(UUID.randomUUID(), parentId, name, kind, Instant.now());
	}

	Category withName(String newName) {
		return new Category(id, parentId, newName, kind, createdAt);
	}

	boolean isTopLevel() {
		return parentId == null;
	}

}
