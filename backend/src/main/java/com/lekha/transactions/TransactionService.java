package com.lekha.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lekha.accounts.AccountService;

@Service
public class TransactionService {

	private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

	/** Metadata keys holding the bank's values once the user has corrected them. */
	static final String ORIGINAL_DATE = "originalTransactionDate";

	static final String ORIGINAL_AMOUNT = "originalAmount";

	private static final int REFUND_WINDOW_DAYS = 120;

	private static final int MAX_REFUND_CANDIDATES = 50;

	private final TransactionRepository repository;

	private final AllocationRepository allocations;

	private final AccountService accounts;

	private final AiSuggestionRepository aiSuggestions;

	TransactionService(TransactionRepository repository, AllocationRepository allocations, AccountService accounts,
			AiSuggestionRepository aiSuggestions) {
		this.repository = repository;
		this.allocations = allocations;
		this.accounts = accounts;
		this.aiSuggestions = aiSuggestions;
	}

	/**
	 * Transactions dated from {@code from} to {@code to}, both inclusive, for one account or all of them. The range is
	 * capped at a year so no request can load the whole history by accident.
	 */
	TransactionList list(@Nullable UUID accountId, LocalDate from, LocalDate to) {
		if (from.isAfter(to)) {
			throw new InvalidDateRangeException("\"from\" (" + from + ") must not be after \"to\" (" + to + ")");
		}
		if (to.isAfter(from.plusYears(1).minusDays(1))) {
			throw new InvalidDateRangeException("The date range must be at most 1 year");
		}
		if (accountId != null) {
			accounts.getAccount(accountId);
		}

		List<Transaction> transactions = repository.findBetween(accountId, from, to);
		List<ListedTransaction> listed = withClassification(transactions);

		BigDecimal moneyIn = sum(transactions, amount -> amount.signum() > 0);
		BigDecimal moneyOut = sum(transactions, amount -> amount.signum() < 0);
		Map<AllocationKind, BigDecimal> totals = allocations.totalsReportedBetween(accountId, from, to);
		return new TransactionList(listed, moneyIn, moneyOut, moneyIn.add(moneyOut),
				total(totals, AllocationKind.EXPENSE).negate(), total(totals, AllocationKind.INCOME),
				total(totals, AllocationKind.INVESTMENT).negate(), total(totals, AllocationKind.LENT).negate(),
				(int) listed.stream().filter(transaction -> !transaction.isClassified()).count());
	}

	/** One transaction as {@link #list} shows it, for opening it from somewhere else, such as a dashboard figure. */
	ListedTransaction get(UUID id) {
		Transaction transaction = repository.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));
		return withClassification(List.of(transaction)).getFirst();
	}

	/**
	 * Purchases the refund could be linked to: classified expenses with money left to refund, from up to 120 days
	 * before it. {@code search} narrows them by description.
	 */
	List<RefundCandidate> refundCandidates(UUID refundId, @Nullable String search) {
		Transaction refund = repository.findById(refundId).orElseThrow(() -> new TransactionNotFoundException(refundId));
		String query = search == null || search.isBlank() ? null : search.strip();
		return allocations.refundCandidates(refund, refund.transactionDate().minusDays(REFUND_WINDOW_DAYS), query,
				MAX_REFUND_CANDIDATES);
	}

	/**
	 * Attaches each transaction's allocations, what has been refunded on it, the purchases it refunds, and the AI's
	 * suggestion if it is unclassified.
	 */
	private List<ListedTransaction> withClassification(List<Transaction> transactions) {
		List<UUID> ids = transactions.stream().map(Transaction::id).toList();
		Map<UUID, List<Allocation>> allocationsById = allocations.findByTransactionIds(ids);
		Map<UUID, AiSuggestion> suggestions = aiSuggestions
			.findByTransactionIds(ids.stream().filter(id -> !allocationsById.containsKey(id)).toList());
		Map<UUID, BigDecimal> refunded = allocations.refundedAmounts(ids);
		Set<UUID> purchaseIds = allocationsById.values()
			.stream()
			.flatMap(List::stream)
			.map(Allocation::refundOf)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
		Map<UUID, Transaction> purchases = repository.findByIds(purchaseIds)
			.stream()
			.collect(Collectors.toMap(Transaction::id, Function.identity()));

		return transactions.stream().map(transaction -> {
			List<Allocation> pieces = allocationsById.getOrDefault(transaction.id(), List.of());
			List<ListedTransaction.RefundedPurchase> refundsOf = pieces.stream()
				.map(Allocation::refundOf)
				.filter(Objects::nonNull)
				.distinct()
				.map(purchases::get)
				.filter(Objects::nonNull)
				.map(ListedTransaction.RefundedPurchase::of)
				.toList();
			return new ListedTransaction(transaction, pieces, refunded.getOrDefault(transaction.id(), ZERO_MONEY),
					refundsOf, suggestions.get(transaction.id()));
		}).toList();
	}

	/**
	 * Saves the transactions that aren't already stored, all or none, and returns the ones saved. Re-uploading an
	 * overlapping statement therefore adds only its new rows.
	 */
	@Transactional
	public List<Transaction> recordNew(List<Transaction> transactions) {
		Map<Fingerprint, Integer> stored = new HashMap<>();
		transactions.stream()
			.map(Transaction::accountId)
			.distinct()
			.flatMap(accountId -> repository.findByAccountId(accountId).stream())
			.forEach(existing -> stored.merge(Fingerprint.of(existing), 1, Integer::sum));

		List<Transaction> saved = new ArrayList<>();
		for (Transaction transaction : transactions) {
			Fingerprint fingerprint = Fingerprint.of(transaction);
			if (stored.getOrDefault(fingerprint, 0) > 0) {
				stored.merge(fingerprint, -1, Integer::sum);
			}
			else {
				repository.insert(transaction);
				saved.add(transaction);
			}
		}
		return saved;
	}

	/**
	 * Corrects a transaction's date, description or amount. The first change to the date or amount keeps the bank's
	 * value in the metadata, so a re-imported statement still recognises the row. A new amount no longer matches the
	 * allocations, so they are cleared and the transaction becomes unclassified.
	 */
	@Transactional
	ListedTransaction update(UUID id, @Nullable LocalDate transactionDate, @Nullable String description,
			@Nullable BigDecimal amount) {
		if (transactionDate == null || description == null || amount == null) {
			throw new InvalidTransactionException("A transaction needs a date, a description and an amount");
		}
		Transaction existing = repository.findByIdForUpdate(id).orElseThrow(() -> new TransactionNotFoundException(id));

		Map<String, String> metadata = new HashMap<>(existing.metadata());
		if (!transactionDate.equals(existing.transactionDate())) {
			metadata.putIfAbsent(ORIGINAL_DATE, existing.transactionDate().toString());
		}
		boolean amountChanged = amount.compareTo(existing.amount()) != 0;
		if (amountChanged) {
			BigDecimal refunded = allocations.refundedAmounts(List.of(id)).getOrDefault(id, ZERO_MONEY);
			if (refunded.signum() > 0) {
				throw new RefundConflictException(refunded.toPlainString() + " of this purchase has been refunded, so "
						+ "its amount can't change. Unlink its refunds first.");
			}
			metadata.putIfAbsent(ORIGINAL_AMOUNT, existing.amount().toPlainString());
		}
		Transaction updated;
		try {
			updated = new Transaction(id, existing.accountId(), transactionDate, existing.settlementDate(), description,
					amount, existing.balanceAfter(), existing.paymentMode(), metadata, existing.createdAt());
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidTransactionException(ex.getMessage());
		}

		repository.update(updated);
		if (amountChanged) {
			allocations.replace(id, List.of());
		}
		if (!description.strip().equals(existing.description())) {
			// The suggestion was for the old description; the next AI run looks at the new one.
			aiSuggestions.deleteFor(id);
		}
		return withClassification(List.of(updated)).getFirst();
	}

	private static BigDecimal sum(List<Transaction> transactions, Predicate<BigDecimal> include) {
		return transactions.stream().map(Transaction::amount).filter(include).reduce(ZERO_MONEY, BigDecimal::add);
	}

	private static BigDecimal total(Map<AllocationKind, BigDecimal> totals, AllocationKind kind) {
		return Money.of(totals.getOrDefault(kind, ZERO_MONEY));
	}

	/**
	 * The facts copied straight from the bank that identify a transaction. Excludes what our parsers derive, like
	 * the description, so improving a parser doesn't make old rows look new. Uses the bank's date and amount even
	 * after the user corrects them, so an edited row still matches the statement it came from.
	 */
	private record Fingerprint(UUID accountId, LocalDate transactionDate, BigDecimal amount,
			@Nullable BigDecimal balanceAfter, @Nullable String reference) {

		static Fingerprint of(Transaction transaction) {
			Map<String, String> metadata = transaction.metadata();
			String date = metadata.get(ORIGINAL_DATE);
			String amount = metadata.get(ORIGINAL_AMOUNT);
			return new Fingerprint(transaction.accountId(),
					date == null ? transaction.transactionDate() : LocalDate.parse(date),
					amount == null ? transaction.amount() : new BigDecimal(amount), transaction.balanceAfter(),
					metadata.get("reference"));
		}

	}

}
