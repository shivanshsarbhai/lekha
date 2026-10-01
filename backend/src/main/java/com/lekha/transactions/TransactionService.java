package com.lekha.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

	private final TransactionRepository repository;

	TransactionService(TransactionRepository repository) {
		this.repository = repository;
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
	 * The facts copied straight from the bank that identify a transaction. Excludes what our parsers derive, like
	 * the description, so improving a parser doesn't make old rows look new.
	 */
	private record Fingerprint(UUID accountId, LocalDate transactionDate, BigDecimal amount,
			@Nullable BigDecimal balanceAfter, @Nullable String reference) {

		static Fingerprint of(Transaction transaction) {
			return new Fingerprint(transaction.accountId(), transaction.transactionDate(), transaction.amount(),
					transaction.balanceAfter(), transaction.metadata().get("reference"));
		}

	}

}
