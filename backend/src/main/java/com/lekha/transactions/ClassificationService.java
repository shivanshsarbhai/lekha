package com.lekha.transactions;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lekha.categories.Category;
import com.lekha.categories.CategoryService;

/** Saves how a transaction's amount divides into expenses, income, transfers, investments and money lent. */
@Service
class ClassificationService {

	private final TransactionRepository transactions;

	private final AllocationRepository allocations;

	private final CategoryService categories;

	ClassificationService(TransactionRepository transactions, AllocationRepository allocations,
			CategoryService categories) {
		this.transactions = transactions;
		this.allocations = allocations;
		this.categories = categories;
	}

	/** Replaces the transaction's allocations. An empty list makes it unclassified. */
	@Transactional
	List<Allocation> classify(UUID transactionId, List<@Nullable AllocationRequest> requests) {
		Transaction transaction = transactions.findByIdForUpdate(transactionId)
			.orElseThrow(() -> new TransactionNotFoundException(transactionId));

		List<Allocation> pieces = requests.stream().map(request -> toAllocation(transactionId, request)).toList();
		pieces.forEach(this::checkCategory);
		if (!pieces.isEmpty()) {
			checkTotal(transaction, pieces);
		}

		allocations.replace(transactionId, pieces);
		return pieces.stream().sorted(Allocation.DISPLAY_ORDER).toList();
	}

	private static Allocation toAllocation(UUID transactionId, @Nullable AllocationRequest request) {
		if (request == null || request.kind() == null || request.amount() == null) {
			throw new InvalidAllocationException("Every allocation needs a kind and an amount");
		}
		try {
			return Allocation.create(transactionId, request.kind(), request.categoryId(), request.amount(),
					request.note());
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidAllocationException(ex.getMessage());
		}
	}

	private void checkCategory(Allocation allocation) {
		UUID categoryId = allocation.categoryId();
		if (categoryId == null) {
			return;
		}
		Category category = categories.findCategory(categoryId)
			.orElseThrow(() -> new InvalidAllocationException("No category with id " + categoryId));
		if (!category.kind().name().equals(allocation.kind().name())) {
			throw new InvalidAllocationException("\"" + category.name() + "\" is " + withArticle(category.kind().name())
					+ " category, so it can't be used for " + withArticle(allocation.kind().name()) + " allocation");
		}
	}

	private static String withArticle(String kind) {
		return ("AEIOU".indexOf(kind.charAt(0)) >= 0 ? "an " : "a ") + kind;
	}

	private static void checkTotal(Transaction transaction, List<Allocation> pieces) {
		BigDecimal total = pieces.stream().map(Allocation::amount).reduce(new BigDecimal("0.00"), BigDecimal::add);
		if (total.compareTo(transaction.amount()) != 0) {
			throw new InvalidAllocationException("Allocations add up to " + total.toPlainString()
					+ " but the transaction is " + transaction.amount().toPlainString());
		}
	}

}
