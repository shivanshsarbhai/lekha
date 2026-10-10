package com.lekha.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.lekha.TestcontainersConfiguration;

@JdbcTest
@Import({ TestcontainersConfiguration.class, AccountRepository.class, AccountService.class })
class AccountServiceTest {

	@Autowired
	private AccountService service;

	@Autowired
	private AccountRepository repository;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void createAccountSavesAndReturnsTheAccount() {
		Account created = service.createAccount("HDFC Salary", AccountType.BANK, Institution.HDFC, "1234");

		assertThat(repository.findById(created.id())).contains(created);
	}

	@Test
	void createAccountRejectsDuplicateNicknameWithAFriendlyMessage() {
		service.createAccount("HDFC Salary", AccountType.BANK, Institution.HDFC, "1234");

		assertThatThrownBy(() -> service.createAccount("hdfc salary", AccountType.CREDIT_CARD, Institution.SBI, null))
				.isInstanceOf(DuplicateAccountNicknameException.class)
				.hasMessage("You already have an account called \"hdfc salary\"")
				.hasCauseInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void createAccountRejectsABlankNicknameAsAnInvalidAccount() {
		assertThatThrownBy(() -> service.createAccount(" ", AccountType.BANK, Institution.HDFC, null))
			.isInstanceOf(InvalidAccountException.class)
			.hasMessage("Nickname must not be blank");
	}

	@Test
	void listAccountsCountsEachAccountsTransactions() {
		Account hdfc = service.createAccount("HDFC Salary", AccountType.BANK, Institution.HDFC, null);
		service.createAccount("SBI Savings", AccountType.BANK, Institution.SBI, null);
		insertTransaction(hdfc.id());
		insertTransaction(hdfc.id());

		assertThat(service.listAccounts()).extracting(ListedAccount::nickname, ListedAccount::transactionCount)
			.containsExactly(tuple("HDFC Salary", 2), tuple("SBI Savings", 0));
	}

	@Test
	void updateAccountSavesEveryFieldWhileTheAccountIsEmpty() {
		Account created = service.createAccount("Regalia", AccountType.BANK, Institution.HDFC, "1234");

		Account updated = service.updateAccount(created.id(), "Regalia Gold", AccountType.CREDIT_CARD,
				Institution.HDFC, null);

		assertThat(updated).isEqualTo(new Account(created.id(), "Regalia Gold", AccountType.CREDIT_CARD,
				Institution.HDFC, null, created.createdAt()));
		assertThat(repository.findById(created.id())).contains(updated);
	}

	@Test
	void updateAccountRenamesAnAccountThatHasTransactions() {
		Account created = service.createAccount("HDFC Salary", AccountType.BANK, Institution.HDFC, null);
		insertTransaction(created.id());

		service.updateAccount(created.id(), "HDFC Main", AccountType.BANK, Institution.HDFC, "4321");

		assertThat(repository.findById(created.id())).get().extracting(Account::nickname, Account::last4)
			.containsExactly("HDFC Main", "4321");
	}

	@Test
	void updateAccountRefusesToChangeTheBankOrTypeOnceTransactionsAreImported() {
		Account created = service.createAccount("HDFC Salary", AccountType.BANK, Institution.HDFC, null);
		insertTransaction(created.id());

		assertThatThrownBy(() -> service.updateAccount(created.id(), "HDFC Salary", AccountType.CREDIT_CARD,
				Institution.HDFC, null))
			.isInstanceOf(AccountInUseException.class)
			.hasMessage("\"HDFC Salary\" already has imported transactions, so its bank and type can't change. "
					+ "Delete it and add the account again instead.");
		assertThatThrownBy(() -> service.updateAccount(created.id(), "HDFC Salary", AccountType.BANK,
				Institution.SBI, null))
			.isInstanceOf(AccountInUseException.class);
		assertThat(repository.findById(created.id())).contains(created);
	}

	@Test
	void updateAccountRejectsANicknameAnotherAccountUses() {
		service.createAccount("HDFC Salary", AccountType.BANK, Institution.HDFC, null);
		Account sbi = service.createAccount("SBI Savings", AccountType.BANK, Institution.SBI, null);

		assertThatThrownBy(() -> service.updateAccount(sbi.id(), "hdfc salary", AccountType.BANK, Institution.SBI,
				null))
			.isInstanceOf(DuplicateAccountNicknameException.class);
	}

	@Test
	void updateAccountRejectsAMissingType() {
		Account created = service.createAccount("HDFC Salary", AccountType.BANK, Institution.HDFC, null);

		assertThatThrownBy(() -> service.updateAccount(created.id(), "HDFC Salary", null, Institution.HDFC, null))
			.isInstanceOf(InvalidAccountException.class)
			.hasMessage("Choose whether this is a bank account or a credit card");
	}

	@Test
	void updateAccountThrowsForAnUnknownAccount() {
		UUID unknown = UUID.randomUUID();

		assertThatThrownBy(() -> service.updateAccount(unknown, "X", AccountType.BANK, Institution.HDFC, null))
			.isInstanceOf(AccountNotFoundException.class);
	}

	@Test
	void deleteAccountAlsoDeletesItsTransactionsAndTheirAllocationsButNoOtherAccounts() {
		Account hdfc = service.createAccount("HDFC Salary", AccountType.BANK, Institution.HDFC, null);
		Account sbi = service.createAccount("SBI Savings", AccountType.BANK, Institution.SBI, null);
		UUID hdfcTransaction = insertTransaction(hdfc.id());
		insertAllocation(hdfcTransaction);
		UUID sbiTransaction = insertTransaction(sbi.id());
		insertAllocation(sbiTransaction);

		service.deleteAccount(hdfc.id());

		assertThat(repository.findById(hdfc.id())).isEmpty();
		assertThat(countWhere("transactions", "account_id", hdfc.id())).isZero();
		assertThat(countWhere("allocations", "transaction_id", hdfcTransaction)).isZero();
		assertThat(countWhere("transactions", "account_id", sbi.id())).isEqualTo(1);
		assertThat(countWhere("allocations", "transaction_id", sbiTransaction)).isEqualTo(1);
	}

	@Test
	void deleteAccountThrowsForAnUnknownAccount() {
		UUID unknown = UUID.randomUUID();

		assertThatThrownBy(() -> service.deleteAccount(unknown)).isInstanceOf(AccountNotFoundException.class);
	}

	@Test
	void getAccountReturnsTheAccountOrThrowsWhenItDoesNotExist() {
		Account created = service.createAccount("HDFC Salary", AccountType.BANK, Institution.HDFC, null);
		UUID unknown = UUID.randomUUID();

		assertThat(service.getAccount(created.id())).isEqualTo(created);
		assertThatThrownBy(() -> service.getAccount(unknown))
				.isInstanceOf(AccountNotFoundException.class)
				.hasMessage("No account with id " + unknown);
	}

	private UUID insertTransaction(UUID accountId) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO transactions (id, account_id, transaction_date, description, amount, metadata, created_at)
				VALUES (:id, :accountId, DATE '2026-09-01', 'TEST', -100.00, '{}'::jsonb, now())
				""")
			.param("id", id)
			.param("accountId", accountId)
			.update();
		return id;
	}

	private void insertAllocation(UUID transactionId) {
		jdbc.sql("""
				INSERT INTO allocations (id, transaction_id, kind, amount)
				VALUES (gen_random_uuid(), :transactionId, 'EXPENSE', -100.00)
				""")
			.param("transactionId", transactionId)
			.update();
	}

	private int countWhere(String table, String column, UUID id) {
		return jdbc.sql("SELECT count(*) FROM " + table + " WHERE " + column + " = :id")
			.param("id", id)
			.query(Integer.class)
			.single();
	}

}
