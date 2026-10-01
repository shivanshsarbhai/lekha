package com.lekha.statements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.lekha.accounts.Account;
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
	void importsWithTheParserThatSupportsTheAccount() {
		given(accounts.findAccount(hdfcBank.id())).willReturn(Optional.of(hdfcBank));
		StatementImportService service = service(new FakeParser(Institution.SBI, AccountType.BANK, List.of()),
				new FakeParser(Institution.HDFC, AccountType.BANK, List.of(SALARY, SWIGGY)));

		given(transactions.recordNew(any())).willAnswer(call -> call.getArgument(0));

		List<Transaction> imported = service.importStatement(hdfcBank.id(), emptyFile()).imported();

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
	void reportsHowManyTransactionsWereAlreadyStored() {
		given(accounts.findAccount(hdfcBank.id())).willReturn(Optional.of(hdfcBank));
		StatementImportService service = service(
				new FakeParser(Institution.HDFC, AccountType.BANK, List.of(SALARY, SWIGGY)));
		given(transactions.recordNew(any())).willAnswer(call -> call.<List<Transaction>>getArgument(0).subList(1, 2));

		StatementImport result = service.importStatement(hdfcBank.id(), emptyFile());

		assertThat(result.imported()).extracting(Transaction::description).containsExactly("UPI-SWIGGY");
		assertThat(result.skipped()).isEqualTo(1);
	}

	@Test
	void rejectsAnUnknownAccount() {
		UUID unknown = UUID.randomUUID();
		given(accounts.findAccount(unknown)).willReturn(Optional.empty());
		StatementImportService service = service(new FakeParser(Institution.HDFC, AccountType.BANK, List.of()));

		assertThatThrownBy(() -> service.importStatement(unknown, emptyFile()))
				.isInstanceOf(AccountNotFoundException.class)
				.hasMessage("No account with id " + unknown);
		then(transactions).shouldHaveNoInteractions();
	}

	@Test
	void rejectsAnAccountNoParserSupports() {
		Account card = Account.create("HDFC Card", AccountType.CREDIT_CARD, Institution.HDFC, null);
		given(accounts.findAccount(card.id())).willReturn(Optional.of(card));
		StatementImportService service = service(new FakeParser(Institution.HDFC, AccountType.BANK, List.of()));

		assertThatThrownBy(() -> service.importStatement(card.id(), emptyFile()))
				.isInstanceOf(UnsupportedAccountException.class)
				.hasMessage("Statements for HDFC CREDIT_CARD accounts can't be imported yet");
		then(transactions).shouldHaveNoInteractions();
	}

	@Test
	void savesNothingWhenTheFileCannotBeParsed() {
		given(accounts.findAccount(hdfcBank.id())).willReturn(Optional.of(hdfcBank));
		StatementParser broken = mock(StatementParser.class);
		given(broken.supports(Institution.HDFC, AccountType.BANK)).willReturn(true);
		given(broken.parse(any())).willThrow(new StatementFormatException("Could not read the file as a PDF"));
		StatementImportService service = service(broken);

		assertThatThrownBy(() -> service.importStatement(hdfcBank.id(), emptyFile()))
				.isInstanceOf(StatementFormatException.class);
		then(transactions).shouldHaveNoInteractions();
	}

	private StatementImportService service(StatementParser... parsers) {
		return new StatementImportService(accounts, transactions, List.of(parsers));
	}

	private static InputStream emptyFile() {
		return new ByteArrayInputStream(new byte[0]);
	}

	private record FakeParser(Institution institution, AccountType type,
			List<StatementEntry> entries) implements StatementParser {

		@Override
		public boolean supports(Institution institution, AccountType type) {
			return this.institution == institution && this.type == type;
		}

		@Override
		public List<StatementEntry> parse(InputStream file) {
			return entries;
		}

	}

}
