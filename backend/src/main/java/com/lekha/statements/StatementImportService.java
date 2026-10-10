package com.lekha.statements;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import com.lekha.accounts.Account;
import com.lekha.accounts.AccountService;
import com.lekha.transactions.AiClassifier;
import com.lekha.transactions.Transaction;
import com.lekha.transactions.TransactionService;

@Service
class StatementImportService {

	private final AccountService accounts;

	private final TransactionService transactions;

	private final List<StatementParser> parsers;

	private final AiClassifier ai;

	StatementImportService(AccountService accounts, TransactionService transactions, List<StatementParser> parsers,
			AiClassifier ai) {
		this.accounts = accounts;
		this.transactions = transactions;
		this.parsers = parsers;
		this.ai = ai;
	}

	/**
	 * Parses the statement fully before saving anything, so a bad file leaves the database untouched. The password,
	 * if any, only unlocks the PDF in memory.
	 */
	StatementImport importStatement(UUID accountId, byte[] pdf, @Nullable String password) {
		Account account = accounts.getAccount(accountId);
		StatementParser parser = parsers.stream()
			.filter(candidate -> candidate.supports(account.institution(), account.type()))
			.findFirst()
			.orElseThrow(() -> new UnsupportedAccountException(account));

		List<String> lines = PdfText.lines(pdf, password);
		if (!parser.recognises(lines)) {
			throw notThisAccountsStatement(account, parser, lines);
		}

		List<Transaction> parsed = parser.parseText(lines)
			.stream()
			.map(entry -> toTransaction(accountId, entry))
			.toList();
		List<Transaction> imported = transactions.recordNew(parsed);
		if (!imported.isEmpty()) {
			ai.suggestInBackground();
		}
		return new StatementImport(imported, parsed.size() - imported.size());
	}

	/** Names the account type the statement belongs to when another parser recognises it. */
	private StatementFormatException notThisAccountsStatement(Account account, StatementParser parser,
			List<String> lines) {
		return parsers.stream()
			.filter(other -> other != parser && other.recognises(lines))
			.findFirst()
			.map(other -> new StatementFormatException("This looks like " + other.accountKind() + " statement, but \""
					+ account.nickname() + "\" is " + parser.accountKind() + " account. Import it into "
					+ other.accountKind() + " account instead."))
			.orElseGet(() -> new StatementFormatException(
					"This doesn't look like " + parser.accountKind() + " account statement"));
	}

	private static Transaction toTransaction(UUID accountId, StatementEntry entry) {
		return Transaction.create(accountId, entry.transactionDate(), entry.settlementDate(), entry.description(),
				entry.amount(), entry.balanceAfter(), entry.paymentMode(), entry.metadata());
	}

}
