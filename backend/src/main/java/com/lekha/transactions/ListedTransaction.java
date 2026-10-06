package com.lekha.transactions;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * A transaction as the list shows it: the bank's facts, plus how it has been classified. An empty list of allocations
 * means it is unclassified. The transaction's fields stay at the top level of the JSON.
 */
record ListedTransaction(@JsonUnwrapped Transaction transaction, List<Allocation> allocations) {

	boolean isClassified() {
		return !allocations.isEmpty();
	}

}
