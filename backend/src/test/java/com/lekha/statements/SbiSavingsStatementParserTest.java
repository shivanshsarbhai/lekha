package com.lekha.statements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.lekha.accounts.AccountType;
import com.lekha.accounts.Institution;
import com.lekha.transactions.PaymentMode;

/** All statement text here is made up, but follows the layout of a real SBI statement as PDFBox extracts it. */
class SbiSavingsStatementParserTest {

	private static final String SUMMARY_VALUES = "1,000.00CR 1,250.00 1,250.00 1,000.00CR3 2";

	private static final List<String> ROWS = List.of(
			"01/09/2026 01/09/2026",
			"WDL TFR",
			"UPI/DR/111122223333/ACME",
			"FOOD/YE",
			"SB/acme@ybl/Lunch",
			"0000000000001 AT 12345 SOME",
			"BRANCH CITY",
			"- 250.00 - 750.00",
			"1Page no.",
			"Balance",
			"02/09/2026 03/09/2026",
			"DEP TFR",
			"UPI/CR/444455556666/JOHN",
			"DOE/HDFC/john.doe@/Split",
			"0000000000002 AT 12345 SOME",
			"BRANCH CITY",
			"- - 1,200.00 1,950.00",
			"03/09/2026 03/09/2026",
			"WDL TFR",
			"IMPS/777788889999/ABCD-xx123-",
			"Landlord/Rent",
			"0000000000003 AT 12345 SOME",
			"BRANCH CITY",
			"- 900.00 - 1,050.00",
			"04/09/2026 04/09/2026 INTEREST CREDIT - - 50.00 1,100.00",
			"05/09/2026 05/09/2026",
			"DIRECT DR   0011223344556 OF",
			"Mr. TEST USER AT 12345 SOME",
			"BRANCH CITY",
			"- 100.00 - 1,000.00");

	private final SbiSavingsStatementParser parser = new SbiSavingsStatementParser();

	@Test
	void supportsOnlySbiBankAccounts() {
		assertThat(parser.supports(Institution.SBI, AccountType.BANK)).isTrue();
		assertThat(parser.supports(Institution.SBI, AccountType.CREDIT_CARD)).isFalse();
		assertThat(parser.supports(Institution.HDFC, AccountType.BANK)).isFalse();
	}

	@Test
	void readsEveryTransactionAcrossPagesWithItsSignFromItsColumn() {
		List<StatementEntry> entries = parser.parseText(statement(ROWS, SUMMARY_VALUES));

		assertThat(entries)
				.extracting(StatementEntry::transactionDate, StatementEntry::settlementDate, StatementEntry::amount,
						StatementEntry::balanceAfter, StatementEntry::paymentMode)
				.containsExactly(
						tuple(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), new BigDecimal("-250.00"),
								new BigDecimal("750.00"), PaymentMode.UPI),
						tuple(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 3), new BigDecimal("1200.00"),
								new BigDecimal("1950.00"), PaymentMode.UPI),
						tuple(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 3), new BigDecimal("-900.00"),
								new BigDecimal("1050.00"), PaymentMode.IMPS),
						tuple(LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 4), new BigDecimal("50.00"),
								new BigDecimal("1100.00"), PaymentMode.OTHER),
						tuple(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 5), new BigDecimal("-100.00"),
								new BigDecimal("1000.00"), PaymentMode.OTHER));
	}

	@Test
	void rejoinsWrappedNarrationsAndDropsTheTransferPrefixAndBranch() {
		List<StatementEntry> entries = parser.parseText(statement(ROWS, SUMMARY_VALUES));

		assertThat(entries).extracting(StatementEntry::description)
			.containsExactly("UPI/DR/111122223333/ACME FOOD/YESB/acme@ybl/Lunch",
					"UPI/CR/444455556666/JOHN DOE/HDFC/john.doe@/Split", "IMPS/777788889999/ABCD-xx123-Landlord/Rent",
					"INTEREST CREDIT", "DIRECT DR 0011223344556 OF Mr. TEST USER");
	}

	@Test
	void keepsTheUpiReferenceAndTheOriginalLines() {
		StatementEntry upi = parser.parseText(statement(ROWS, SUMMARY_VALUES)).getFirst();

		assertThat(upi.metadata()).isEqualTo(Map.of("reference", "111122223333", "rawNarration",
				"WDL TFR\nUPI/DR/111122223333/ACME\nFOOD/YE\nSB/acme@ybl/Lunch\n0000000000001 AT 12345 SOME\nBRANCH CITY"));
	}

	@Test
	void leavesTheReferenceOutWhenTheNarrationHasNone() {
		StatementEntry transfer = parser.parseText(statement(ROWS, SUMMARY_VALUES)).getLast();

		assertThat(transfer.metadata()).containsOnlyKeys("rawNarration");
	}

	@Test
	void rejectsARowWhoseBalanceDoesNotAddUp() {
		List<String> rows = new ArrayList<>(ROWS);
		rows.set(7, "- 250.00 - 760.00");

		assertThatThrownBy(() -> parser.parseText(statement(rows, SUMMARY_VALUES)))
				.isInstanceOf(StatementFormatException.class)
				.hasMessage("Balance does not add up for the transaction on 2026-09-01: "
						+ "UPI/DR/111122223333/ACME FOOD/YESB/acme@ybl/Lunch");
	}

	@Test
	void rejectsARowWithBothADebitAndACredit() {
		List<String> rows = new ArrayList<>(ROWS);
		rows.set(7, "- 250.00 250.00 750.00");

		assertThatThrownBy(() -> parser.parseText(statement(rows, SUMMARY_VALUES)))
				.isInstanceOf(StatementFormatException.class)
				.hasMessage("Expected either a debit or a credit for the transaction on 2026-09-01");
	}

	@Test
	void rejectsAStatementWhoseCountsDoNotMatchTheSummary() {
		assertThatThrownBy(() -> parser.parseText(statement(ROWS, "1,000.00CR 1,250.00 1,250.00 1,000.00CR4 2")))
				.isInstanceOf(StatementFormatException.class)
				.hasMessage("Statement summary mismatch for Dr Count: the summary says 4 but the transactions give 3");
	}

	@Test
	void rejectsAStatementWhoseTotalsDoNotMatchTheSummary() {
		assertThatThrownBy(() -> parser.parseText(statement(ROWS, "1,000.00CR 1,250.00 1,260.00 1,000.00CR3 2")))
				.isInstanceOf(StatementFormatException.class)
				.hasMessage("Statement summary mismatch for Total Credits: the summary says 1260.00 but the "
						+ "transactions give 1250.00");
	}

	@Test
	void rejectsTextAfterTheAmounts() {
		List<String> rows = new ArrayList<>(ROWS);
		rows.add(8, "SOMETHING ELSE");

		assertThatThrownBy(() -> parser.parseText(statement(rows, SUMMARY_VALUES)))
				.isInstanceOf(StatementFormatException.class)
				.hasMessage("Unexpected text after the amounts on 2026-09-01: SOMETHING ELSE");
	}

	@Test
	void rejectsAStatementFromAnotherBank() {
		List<String> lines = statement(ROWS, SUMMARY_VALUES).stream()
			.filter(line -> !line.contains("State Bank of India"))
			.toList();

		assertThatThrownBy(() -> parser.parseText(lines)).isInstanceOf(StatementFormatException.class)
			.hasMessage("This doesn't look like an SBI account statement");
	}

	@Test
	void readsTheSameStatementFromAPdf() throws IOException {
		byte[] pdf = TestPdfs.withLines(statement(ROWS, SUMMARY_VALUES));

		assertThat(parser.parseText(PdfText.lines(pdf, null)))
				.isEqualTo(parser.parseText(statement(ROWS, SUMMARY_VALUES)));
	}

	@Test
	void onlyThisParserRecognisesTheStatement() {
		List<String> lines = statement(ROWS, SUMMARY_VALUES);

		assertThat(parser.recognises(lines)).isTrue();
		assertThat(new SbiCreditCardStatementParser().recognises(lines)).isFalse();
		assertThat(new HdfcSavingsStatementParser().recognises(lines)).isFalse();
		assertThat(new HdfcCreditCardStatementParser().recognises(lines)).isFalse();
	}

	/** Wraps transaction lines in the account details, summary and footer that surround them in a real statement. */
	private static List<String> statement(List<String> rows, String summaryValues) {
		List<String> lines = new ArrayList<>();
		lines.add("Account Summary");
		lines.add("Statement From : 01-09-2026 to 30-09-2026");
		lines.add("STATEMENT OF ACCOUNT");
		lines.add(" State Bank of India");
		lines.add("Branch Name : SOMEWHERE");
		lines.add("Balance");
		lines.addAll(rows);
		lines.add("2Page no.");
		lines.add("Statement Summary : 01-09-2026 To 30-09-2026");
		lines.add("Brought Forward Dr Count Cr Count Total Debits Total Credits Closing Balance");
		lines.add(summaryValues);
		lines.add("This is a computer generated statement and does not require a signature.");
		return lines;
	}

}
