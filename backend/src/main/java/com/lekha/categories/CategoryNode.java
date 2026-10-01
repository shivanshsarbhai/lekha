package com.lekha.categories;

import java.util.List;
import java.util.UUID;

/** A top-level category with its sub-categories, as shown in pickers and reports. Sub-categories have no children. */
record CategoryNode(UUID id, String name, CategoryKind kind, List<CategoryNode> children) {

	CategoryNode {
		children = List.copyOf(children);
	}

}
