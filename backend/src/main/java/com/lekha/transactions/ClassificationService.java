package com.lekha.transactions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lekha.categories.Category;
import com.lekha.categories.CategoryService;

/** Saves how a transaction's amount divides into expenses, income, transfers, investments and money lent. */
@Service
class ClassificationService {

	private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

	private static final int MAX_BULK = 500;

	private final TransactionRepository transactions;

	private final AllocationRepository allocations;

	private final CategoryService categories;

	ClassificationService(TransactionRepository transactions, AllocationRepository allocations,
			CategoryService categories) {
		this.transactions = transactions;
		this.allocations = allocations;
		this.categories = categories;
	}

	/**
	 * Replaces the transaction's allocations. An empty list makes it unclassified. Refund pieces are checked against
	 * the purchases they refund, and a purchase that has refunds keeps enough spending in their categories to cover them.
	 */
	@Transactional
	List<Allocation> classify(UUID transactionId, List<@Nullable AllocationRequest> requests) {
		Transaction transaction = transactions.findByIdForUpdate(transactionId)
			.orElseThrow(() -> new TransactionNotFoundException(transactionId));

		List<Allocation> pieces = requests.stream().map(request -> toAllocation(transactionId, request)).toList();
		pieces.forEach(this::checkCategory);
		if (!pieces.isEmpty()) {
			checkTotal(transaction, pieces);
		}
		checkRefunds(transactionId, pieces);
		checkStillCoversItsRefunds(transaction, pieces);

		allocations.replace(transactionId, pieces);
		return pieces.stream().sorted(Allocation.DISPLAY_ORDER).toList();
	}

	/**
	 * Classifies each transaction as one piece of the same kind and category covering its whole amount, replacing
	 * what it had. It runs as one database transaction, so one transaction failing a rule saves none of them; the
	 * error names it.
	 */
	@Transactional
	Map<UUID, List<Allocation>> classifyAll(BulkClassificationRequest request) {
		List<UUID> ids = checkedIds(request.transactionIds());
		AllocationKind kind = request.kind();
		if (kind == null) {
			throw new InvalidAllocationException("Choose a kind to classify the transactions as");
		}

		// Locking in one fixed order means two overlapping bulk saves wait for each other instead of deadlocking.
		Map<UUID, Transaction> locked = new LinkedHashMap<>();
		for (UUID id : ids.stream().sorted().toList()) {
			locked.put(id, transactions.findByIdForUpdate(id).orElseThrow(() -> new TransactionNotFoundException(id)));
		}

		Transaction first = locked.get(ids.getFirst());
		checkCategory(toAllocation(first.id(), wholeAmount(first, kind, request)));

		Map<UUID, List<Allocation>> saved = new LinkedHashMap<>();
		for (UUID id : ids) {
			Transaction transaction = locked.get(id);
			try {
				saved.put(id, classify(id, List.of(wholeAmount(transaction, kind, request))));
			}
			catch (InvalidAllocationException ex) {
				throw new InvalidAllocationException(quoted(transaction) + ": " + ex.getMessage());
			}
			catch (RefundConflictException ex) {
				throw new RefundConflictException(quoted(transaction) + ": " + ex.getMessage());
			}
		}
		return saved;
	}

	private static List<UUID> checkedIds(@Nullable List<@Nullable UUID> ids) {
		if (ids == null || ids.isEmpty()) {
			throw new InvalidAllocationException("Choose at least one transaction to classify");
		}
		if (ids.size() > MAX_BULK) {
			throw new InvalidAllocationException(
					"At most " + MAX_BULK + " transactions can be classified at once, not " + ids.size());
		}
		List<UUID> checked = new ArrayList<>();
		for (UUID id : ids) {
			if (id == null) {
				throw new InvalidAllocationException("Transaction ids can't be empty");
			}
			checked.add(id);
		}
		if (Set.copyOf(checked).size() != checked.size()) {
			throw new InvalidAllocationException("Each transaction can only be listed once");
		}
		return checked;
	}

	private static AllocationRequest wholeAmount(Transaction transaction, AllocationKind kind,
			BulkClassificationRequest request) {
		return new AllocationRequest(kind, request.categoryId(), transaction.amount(), request.note(), null);
	}

	private static Allocation toAllocation(UUID transactionId, @Nullable AllocationRequest request) {
		if (request == null || request.kind() == null || request.amount() == null) {
			throw new InvalidAllocationException("Every allocation needs a kind and an amount");
		}
		try {
			return Allocation.create(transactionId, request.kind(), request.categoryId(), request.amount(),
					request.note(), request.refundOf());
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
		BigDecimal total = sum(pieces);
		if (total.compareTo(transaction.amount()) != 0) {
			throw new InvalidAllocationException("Allocations add up to " + total.toPlainString()
					+ " but the transaction is " + transaction.amount().toPlainString());
		}
	}

	/**
	 * Each purchase this transaction refunds must be a spend, in the same category, with enough left unrefunded. The
	 * purchase row is locked first, so two refunds saved at once can't both take its last rupees.
	 */
	private void checkRefunds(UUID transactionId, List<Allocation> pieces) {
		Map<UUID, List<Allocation>> byPurchase = pieces.stream()
			.filter(Allocation::isRefundLink)
			.collect(Collectors.groupingBy(Allocation::refundOf, LinkedHashMap::new, Collectors.toList()));

		for (Map.Entry<UUID, List<Allocation>> entry : byPurchase.entrySet()) {
			UUID purchaseId = entry.getKey();
			Transaction purchase = transactions.findByIdForUpdate(purchaseId)
				.orElseThrow(() -> new InvalidAllocationException("No transaction with id " + purchaseId + " to refund"));
			List<Allocation> spends = allocations.findByTransactionIds(List.of(purchaseId))
				.getOrDefault(purchaseId, List.of())
				.stream()
				.filter(Allocation::isSpend)
				.toList();
			if (spends.isEmpty()) {
				throw new InvalidAllocationException(
						quoted(purchase) + " isn't classified as an expense, so it can't be refunded");
			}

			Set<@Nullable UUID> purchaseCategories = spends.stream().map(Allocation::categoryId).collect(Collectors.toSet());
			for (Allocation refund : entry.getValue()) {
				if (!purchaseCategories.contains(refund.categoryId())) {
					throw new InvalidAllocationException(
							"A refund must use the same category as the purchase it refunds, " + quoted(purchase));
				}
			}

			BigDecimal spent = sum(spends).negate();
			BigDecimal refundedElsewhere = allocations.refundsOf(purchaseId)
				.stream()
				.filter(link -> !link.transactionId().equals(transactionId))
				.map(AllocationRepository.RefundLink::amount)
				.reduce(ZERO_MONEY, BigDecimal::add);
			BigDecimal refunded = refundedElsewhere.add(sum(entry.getValue()));
			if (refunded.compareTo(spent) > 0) {
				throw new InvalidAllocationException("Refunds of " + quoted(purchase) + " would add up to "
						+ refunded.toPlainString() + ", more than the " + spent.toPlainString() + " spent on it");
			}
		}
	}

	/** A purchase can't be re-classified to spend less, or in other categories, than its linked refunds give back. */
	private void checkStillCoversItsRefunds(Transaction purchase, List<Allocation> pieces) {
		List<AllocationRepository.RefundLink> refunds = allocations.refundsOf(purchase.id());
		if (refunds.isEmpty()) {
			return;
		}
		List<Allocation> spends = pieces.stream().filter(Allocation::isSpend).toList();
		BigDecimal spent = sum(spends).negate();
		BigDecimal refunded = refunds.stream()
			.map(AllocationRepository.RefundLink::amount)
			.reduce(ZERO_MONEY, BigDecimal::add);
		String names = refunds.stream()
			.map(link -> "\"" + link.description() + "\"")
			.distinct()
			.collect(Collectors.joining(", "));
		if (spent.compareTo(refunded) < 0) {
			throw new RefundConflictException(refunded.toPlainString() + " of this purchase was refunded by " + names
					+ ", so it must stay an expense of at least that much. Unlink the refund first.");
		}
		Set<@Nullable UUID> categoriesKept = spends.stream().map(Allocation::categoryId).collect(Collectors.toSet());
		if (!refunds.stream().allMatch(link -> categoriesKept.contains(link.categoryId()))) {
			throw new RefundConflictException("This purchase was refunded by " + names
					+ " in a category it would no longer have. Unlink the refund first.");
		}
	}

	private static String quoted(Transaction transaction) {
		return "\"" + transaction.description() + "\" on " + transaction.transactionDate();
	}

	private static BigDecimal sum(List<Allocation> pieces) {
		return pieces.stream().map(Allocation::amount).reduce(ZERO_MONEY, BigDecimal::add);
	}

}
