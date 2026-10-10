package com.lekha.transactions;

import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * What the language model thinks an unclassified transaction is: one piece of {@code kind} and {@code categoryId} for
 * the whole amount. It is never saved as a classification until the user accepts it.
 */
record AiSuggestion(@JsonIgnore UUID transactionId, AllocationKind kind, @Nullable UUID categoryId, String reason,
		String model) {

	static final int MAX_REASON_LENGTH = 300;

	boolean sameChoiceAs(AllocationKind otherKind, @Nullable UUID otherCategoryId) {
		return kind == otherKind && Objects.equals(categoryId, otherCategoryId);
	}

}
