package com.lekha.statements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.lekha.accounts.Account;
import com.lekha.accounts.AccountNotFoundException;
import com.lekha.accounts.AccountService;
import com.lekha.accounts.AccountType;
import com.lekha.accounts.Institution;
import com.lekha.transactions.PaymentMode;
import com.lekha.transactions.Transaction;
import com.lekha.transactions.TransactionService;

class StatementImportServiceTest {

	private static final StatementEntry SALARY = new StatementEntry(LocalDate.of(2026, 9, 1),
			LocalDate.of(2026, 9, 2), "NEFT CR-ACME CORP-SALARY", new BigDecimal("150000.00"),
			new BigDecimal("160000.00"), PaymentMode.NEFT, Map.of("reference", "0000000012345678"));

	private static final StatementEntry SWIGGY = new StatementEntry(LocalDate.of(2026, 9, 3), null, "UPI-SWIGGY",
			new BigDecimal("-450.00"), new BigDecimal("159550.00"), PaymentMode.UPI, Map.of());

	private final AccountService accounts = mock(AccountService.class);

	private final TransactionService transactions = mock(TransactionService.class);

	private final Account hdfcBank = Account.create("HDFC Salary", AccountType.BANK, Institution.HDFC, null);

	@Test
	void importsWithTheParserThatSupportsTheAccount() throws IOException {
		given(accounts.getAccount(hdfcBank.id())).willReturn(hdfcBank);
		StatementImportService service = service(
				new FakeParser(Institution.SBI, AccountType.BANK, "SBI MARKER", List.of()),
				new FakeParser(Institution.HDFC, AccountType.BANK, "HDFC MARKER", List.of(SALARY, SWIGGY)));

		given(transactions.recordNew(any())).willAnswer(call -> call.getArgument(0));

		List<Transaction> imported = service.importStatement(hdfcBank.id(), pdf("HDFC MARKER"), null).imported();

		assertThat(imported)
				.extracting(Transaction::accountId, Transaction::transactionDate, Transaction::settlementDate,
						Transaction::description, Transaction::amount, Transaction::balanceAfter,
						Transaction::paymentMode, Transaction::metadata)
				.containsExactly(
						tuple(hdfcBank.id(), SALARY.transactionDate(), SALARY.settlementDate(), SALARY.description(),
								SALARY.amount(), SALARY.balanceAfter(), SALARY.paymentMode(), SALARY.metadata()),
						tuple(hdfcBank.id(), SWIGGY.transactionDate(), null, SWIGGY.description(), SWIGGY.amount(),
								SWIGGY.balanceAfter(), SWIGGY.paymentMode(), Map.of()));
		then(transactions).should().recordNew(imported);
	}

	@Test
	void reportsHowManyTransactionsWereAlreadyStored() throws IOException {
		given(accounts.getAccount(hdfcBank.id())).willReturn(hdfcBank);
		StatementImportService service = service(
				new FakeParser(Institution.HDFC, AccountType.BANK, "HDFC MARKER", List.of(SALARY, SWIGGY)));
		given(transactions.recordNew(any())).willAnswer(call -> call.<List<Transaction>>getArgument(0).subList(1, 2));

		StatementImport result = service.importStatement(hdfcBank.id(), pdf("HDFC MARKER"), null);

		assertThat(result.imported()).extracting(Transaction::description).containsExactly("UPI-SWIGGY");
		assertThat(result.skipped()).isEqualTo(1);
	}

	@Test
	void unlocksALockedStatementWithItsPassword() throws IOException {
		given(accounts.getAccount(hdfcBank.id())).willReturn(hdfcBank);
		StatementImportService service = service(
				new FakeParser(Institution.HDFC, AccountType.BANK, "HDFC MARKER", List.of(SALARY)));
		given(transactions.recordNew(any())).willAnswer(call -> call.getArgument(0));
		byte[] locked = TestPdfs.locked(List.of("HDFC MARKER"), "TEST0101");

		assertThatThrownBy(() -> service.importStatement(hdfcBank.id(), locked, null))
			.isInstanceOf(StatementPasswordException.class);
		assertThat(service.importStatement(hdfcBank.id(), locked, "TEST0101").imported()).hasSize(1);
	}

	@Test
	void namesTheRightAccountTypeForAnotherAccountsStatement() throws IOException {
		given(accounts.getAccount(hdfcBank.id())).willReturn(hdfcBank);
		StatementImportService service = service(
				new FakeParser(Institution.HDFC, AccountType.BANK, "HDFC MARKER", List.of(SALARY)),
				new FakeParser(Institution.HDFC, AccountType.CREDIT_CARD, "CARD MARKER", List.of()));

		assertThatThrownBy(() -> service.importStatement(hdfcBank.id(), pdf("CARD MARKER"), null))
			.isInstanceOf(StatementFormatException.class)
			.hasMessage("This looks like an HDFC CREDIT_CARD statement, but \"HDFC Salary\" is an HDFC BANK account. "
					+ "Import it into an HDFC CREDIT_CARD account instead.");
		then(transactions).shouldHaveNoInteractions();
	}

	@Test
	void saysSoWhenNoParserRecognisesTheStatement() throws IOException {
		given(accounts.getAccount(hdfcBank.id())).willReturn(hdfcBank);
		StatementImportService service = service(
				new FakeParser(Institution.HDFC, AccountType.BANK, "HDFC MARKER", List.of(SALARY)));

		assertThatThrownBy(() -> service.importStatement(hdfcBank.id(), pdf("A restaurant menu"), null))
			.isInstanceOf(StatementFormatException.class)
			.hasMessage("This doesn't look like an HDFC BANK account statement");
		then(transactions).shouldHaveNoInteractions();
	}

	@Test
	void savesNothingForAnUnknownAccount() throws IOException {
		UUID unknown = UUID.randomUUID();
		given(accounts.getAccount(unknown)).willThrow(new AccountNotFoundException(unknown));
		StatementImportService service = service(
				new FakeParser(Institution.HDFC, AccountType.BANK, "HDFC MARKER", List.of()));

		assertThatThrownBy(() -> service.importStatement(unknown, pdf("HDFC MARKER"), null))
				.isInstanceOf(AccountNotFoundException.class)
				.hasMessage("No account with id " + unknown);
		then(transactions).shouldHaveNoInteractions();
	}

	@Test
	void rejectsAnAccountNoParserSupports() throws IOException {
		Account card = Account.create("HDFC Card", AccountType.CREDIT_CARD, Institution.HDFC, null);
		given(accounts.getAccount(card.id())).willReturn(card);
		StatementImportService service = service(
				new FakeParser(Institution.HDFC, AccountType.BANK, "HDFC MARKER", List.of()));

		assertThatThrownBy(() -> service.importStatement(card.id(), pdf("HDFC MARKER"), null))
				.isInstanceOf(UnsupportedAccountException.class)
				.hasMessage("Statements for HDFC CREDIT_CARD accounts can't be imported yet");
		then(transactions).shouldHaveNoInteractions();
	}

	@Test
	void savesNothingWhenTheFileCannotBeParsed() {
		given(accounts.getAccount(hdfcBank.id())).willReturn(hdfcBank);
		StatementImportService service = service(
				new FakeParser(Institution.HDFC, AccountType.BANK, "HDFC MARKER", List.of()));

		assertThatThrownBy(() -> service.importStatement(hdfcBank.id(), "not a pdf".getBytes(), null))
				.isInstanceOf(StatementFormatException.class);
		then(transactions).shouldHaveNoInteractions();
	}

	private StatementImportService service(StatementParser... parsers) {
		return new StatementImportService(accounts, transactions, List.of(parsers));
	}

	private static byte[] pdf(String text) throws IOException {
		return TestPdfs.withLines(List.of(text));
	}

	/** Recognises any text containing {@code marker} and reads it as {@code entries}. */
	private record FakeParser(Institution institution, AccountType type, String marker,
			List<StatementEntry> entries) implements StatementParser {

		@Override
		public boolean supports(Institution institution, AccountType type) {
			return this.institution == institution && this.type == type;
		}

		@Override
		public String accountKind() {
			return "an " + institution + " " + type;
		}

		@Override
		public boolean recognises(List<String> lines) {
			return lines.stream().anyMatch(line -> line.contains(marker));
		}

		@Override
		public List<StatementEntry> parseText(List<String> lines) {
			return entries;
		}

	}

}
