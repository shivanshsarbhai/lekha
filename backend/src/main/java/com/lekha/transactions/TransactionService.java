package com.lekha.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

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

	private final TransactionRepository repository;

	private final AllocationRepository allocations;

	private final AccountService accounts;

	TransactionService(TransactionRepository repository, AllocationRepository allocations, AccountService accounts) {
		this.repository = repository;
		this.allocations = allocations;
		this.accounts = accounts;
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
		Map<UUID, List<Allocation>> allocationsById = allocations
			.findByTransactionIds(transactions.stream().map(Transaction::id).toList());
		List<ListedTransaction> listed = transactions.stream()
			.map(transaction -> new ListedTransaction(transaction,
					allocationsById.getOrDefault(transaction.id(), List.of())))
			.toList();
		List<Allocation> pieces = allocationsById.values().stream().flatMap(List::stream).toList();

		BigDecimal moneyIn = sum(transactions, amount -> amount.signum() > 0);
		BigDecimal moneyOut = sum(transactions, amount -> amount.signum() < 0);
		return new TransactionList(listed, moneyIn, moneyOut, moneyIn.add(moneyOut),
				sum(pieces, AllocationKind.EXPENSE).negate(), sum(pieces, AllocationKind.INCOME),
				sum(pieces, AllocationKind.INVESTMENT).negate(), sum(pieces, AllocationKind.LENT).negate(),
				(int) listed.stream().filter(transaction -> !transaction.isClassified()).count());
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
		return new ListedTransaction(updated,
				allocations.findByTransactionIds(List.of(id)).getOrDefault(id, List.of()));
	}

	private static BigDecimal sum(List<Transaction> transactions, Predicate<BigDecimal> include) {
		return transactions.stream().map(Transaction::amount).filter(include).reduce(ZERO_MONEY, BigDecimal::add);
	}

	private static BigDecimal sum(List<Allocation> pieces, AllocationKind kind) {
		return pieces.stream()
			.filter(piece -> piece.kind() == kind)
			.map(Allocation::amount)
			.reduce(ZERO_MONEY, BigDecimal::add);
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
