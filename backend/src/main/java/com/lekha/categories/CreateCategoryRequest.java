package com.lekha.categories;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Body of POST /api/v1/categories. Leave out {@code parentId} for a top-level category, and {@code kind} for a sub-category. */
record CreateCategoryRequest(@Nullable String name, @Nullable CategoryKind kind, @Nullable UUID parentId) {
}
