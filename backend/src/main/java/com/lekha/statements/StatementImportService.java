package com.lekha.statements;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.lekha.accounts.Account;
import com.lekha.accounts.AccountService;
import com.lekha.transactions.Transaction;
import com.lekha.transactions.TransactionService;

@Service
class StatementImportService {

	private final AccountService accounts;

	private final TransactionService transactions;

	private final List<StatementParser> parsers;

	StatementImportService(AccountService accounts, TransactionService transactions, List<StatementParser> parsers) {
		this.accounts = accounts;
		this.transactions = transactions;
		this.parsers = parsers;
	}

	/** Parses the statement fully before saving anything, so a bad file leaves the database untouched. */
	List<Transaction> importStatement(UUID accountId, InputStream file) {
		Account account = accounts.findAccount(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
		StatementParser parser = parsers.stream()
			.filter(candidate -> candidate.supports(account.institution(), account.type()))
			.findFirst()
			.orElseThrow(() -> new UnsupportedAccountException(account));

		List<Transaction> imported = parser.parse(file).stream().map(entry -> toTransaction(accountId, entry)).toList();
		transactions.recordAll(imported);
		return imported;
	}

	private static Transaction toTransaction(UUID accountId, StatementEntry entry) {
		return Transaction.create(accountId, entry.transactionDate(), entry.settlementDate(), entry.description(),
				entry.amount(), entry.balanceAfter(), entry.paymentMode(), entry.metadata());
	}

}
