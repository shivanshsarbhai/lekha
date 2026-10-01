package com.lekha.categories;

import org.jspecify.annotations.Nullable;

/** Body of PATCH /api/v1/categories/{id}. */
record RenameCategoryRequest(@Nullable String name) {
}
