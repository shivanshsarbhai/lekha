package com.lekha.transactions;

/** What a piece of a transaction's money was. Only some kinds are described further by a category. */
public enum AllocationKind {

	EXPENSE(true), INCOME(true), TRANSFER(true), INVESTMENT(true), LENT(false);

	private final boolean hasCategories;

	AllocationKind(boolean hasCategories) {
		this.hasCategories = hasCategories;
	}

	public boolean hasCategories() {
		return hasCategories;
	}

}
