package com.lekha.accounts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

	private final AccountRepository repository;

	AccountService(AccountRepository repository) {
		this.repository = repository;
	}

	List<ListedAccount> listAccounts() {
		return repository.findAllWithTransactionCounts();
	}

	public Optional<Account> findAccount(UUID id) {
		return repository.findById(id);
	}

	/** For callers where a missing account is an error, not an expected answer. */
	public Account getAccount(UUID id) {
		return findAccount(id).orElseThrow(() -> new AccountNotFoundException(id));
	}

	Account createAccount(@Nullable String nickname, @Nullable AccountType type, @Nullable Institution institution,
			@Nullable String last4) {
		Account account = validated(type, institution, () -> Account.create(nonNull(nickname), type, institution, last4));
		saveUnique(account.nickname(), () -> repository.insert(account));
		return account;
	}

	/**
	 * Replaces the account's details. The bank and type decide which parser reads its statements, so they can only
	 * change while nothing has been imported into it.
	 */
	@Transactional
	Account updateAccount(UUID id, @Nullable String nickname, @Nullable AccountType type,
			@Nullable Institution institution, @Nullable String last4) {
		Account existing = getAccount(id);
		Account updated = validated(type, institution,
				() -> new Account(id, nonNull(nickname), type, institution, last4, existing.createdAt()));
		boolean changesKind = updated.type() != existing.type() || updated.institution() != existing.institution();
		if (changesKind && repository.hasTransactions(id)) {
			throw new AccountInUseException("\"" + existing.nickname() + "\" already has imported transactions, so "
					+ "its bank and type can't change. Delete it and add the account again instead.");
		}
		saveUnique(updated.nickname(), () -> repository.update(updated));
		return updated;
	}

	/** Deletes the account together with all its transactions and their classifications. */
	@Transactional
	void deleteAccount(UUID id) {
		getAccount(id);
		repository.delete(id);
	}

	/** Turns missing fields and the record's own validation errors into a 400 rather than a 500. */
	private static Account validated(@Nullable AccountType type, @Nullable Institution institution,
			Supplier<Account> build) {
		if (type == null) {
			throw new InvalidAccountException("Choose whether this is a bank account or a credit card");
		}
		if (institution == null) {
			throw new InvalidAccountException("Choose the bank");
		}
		try {
			return build.get();
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidAccountException(ex.getMessage());
		}
	}

	private static void saveUnique(String nickname, Runnable save) {
		try {
			save.run();
		}
		catch (DuplicateKeyException ex) {
			throw new DuplicateAccountNicknameException(nickname, ex);
		}
	}

	private static String nonNull(@Nullable String nickname) {
		return nickname == null ? "" : nickname;
	}

}
