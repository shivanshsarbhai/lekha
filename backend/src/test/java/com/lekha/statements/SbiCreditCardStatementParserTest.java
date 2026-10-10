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

class SbiCreditCardStatementParserTest {

	/** Credits 7,000.00; purchases 7,250.50 and fees 18.00. */
	private static final List<String> ROWS = List.of(
			"02 Sep 26 ACME BOOKS               Mumbai        IN 1,250.50 D",
			"05 Sep 26 TEST STORE PAYMENTS      Bangalore     IN (Pay in EMIs) 6,000.00 D",
			"06 Sep 26 TEST STORE PAYMENTS      Bangalore     IN 6,000.00 C",
			"10 Sep 26 PAYMENT RECEIVED 000000000000 1,000.00 C",
			"12 Sep 26 IGST DB @ 18.00% 18.00 D");

	private static final String SUMMARY_VALUES = "7,000.00 7,250.50 18.00";

	/** Total Outstanding 769.00 (768.75 rounded), then Previous Balance 500.25 owed. */
	private static final String BALANCES = "769.00500.25";

	private final SbiCreditCardStatementParser parser = new SbiCreditCardStatementParser();

	@Test
	void supportsOnlySbiCreditCards() {
		assertThat(parser.supports(Institution.SBI, AccountType.CREDIT_CARD)).isTrue();
		assertThat(parser.supports(Institution.SBI, AccountType.BANK)).isFalse();
		assertThat(parser.supports(Institution.HDFC, AccountType.CREDIT_CARD)).isFalse();
	}

	@Test
	void readsEveryRowWithItsDirectionAndColumns() {
		List<StatementEntry> entries = parser.parseText(statement(ROWS, SUMMARY_VALUES, BALANCES));

		assertThat(entries).extracting(StatementEntry::transactionDate, StatementEntry::description,
				StatementEntry::amount)
			.containsExactly(tuple(LocalDate.of(2026, 9, 2), "ACME BOOKS", new BigDecimal("-1250.50")),
					tuple(LocalDate.of(2026, 9, 5), "TEST STORE PAYMENTS", new BigDecimal("-6000.00")),
					tuple(LocalDate.of(2026, 9, 6), "TEST STORE PAYMENTS", new BigDecimal("6000.00")),
					tuple(LocalDate.of(2026, 9, 10), "PAYMENT RECEIVED 000000000000", new BigDecimal("1000.00")),
					tuple(LocalDate.of(2026, 9, 12), "IGST DB @ 18.00%", new BigDecimal("-18.00")));
		assertThat(entries).allSatisfy(entry -> {
			assertThat(entry.paymentMode()).isEqualTo(PaymentMode.CARD);
			assertThat(entry.balanceAfter()).isNull();
			assertThat(entry.settlementDate()).isNull();
		});
		assertThat(entries.get(0).metadata()).isEqualTo(Map.of("rawNarration",
				"ACME BOOKS               Mumbai        IN", "city", "Mumbai", "country", "IN"));
		assertThat(entries.get(1).metadata()).containsEntry("payInEmis", "true").containsEntry("city", "Bangalore");
		assertThat(entries.get(3).metadata()).containsOnlyKeys("rawNarration");
	}

	@Test
	void readsAPreviousBalanceInYourFavour() {
		// -100.00 - 7,000.00 + 7,268.50 = 168.50, printed rounded as 169.00.
		assertThat(parser.parseText(statement(ROWS, SUMMARY_VALUES, "169.00100.00 CR"))).hasSize(5);
	}

	@Test
	void acceptsATotalOutstandingPrintedWithPaise() {
		assertThat(parser.parseText(statement(ROWS, SUMMARY_VALUES, "768.75500.25"))).hasSize(5);
	}

	@Test
	void rejectsCreditsThatDoNotMatchTheSummary() {
		assertThatThrownBy(() -> parser.parseText(statement(ROWS, "7,100.00 7,250.50 18.00", BALANCES)))
			.isInstanceOf(StatementFormatException.class)
			.hasMessage("Statement summary mismatch for Payments, Reversals & other Credits: the summary says 7100.00 "
					+ "but the transactions give 7000.00");
	}

	@Test
	void rejectsDebitsThatDoNotMatchPurchasesPlusFees() {
		assertThatThrownBy(() -> parser.parseText(statement(ROWS, "7,000.00 7,250.50 0.00", BALANCES)))
			.isInstanceOf(StatementFormatException.class)
			.hasMessage("Statement summary mismatch for Purchases & Other Debits plus Fee, Taxes & Interest Charges: "
					+ "the summary says 7250.50 but the transactions give 7268.50");
	}

	@Test
	void rejectsATotalOutstandingThatDoesNotAddUp() {
		assertThatThrownBy(() -> parser.parseText(statement(ROWS, SUMMARY_VALUES, "770.00500.25")))
			.isInstanceOf(StatementFormatException.class)
			.hasMessage("Statement summary mismatch for Total Outstanding: the summary says 770.00 but the "
					+ "transactions give 769.00");
	}

	@Test
	void rejectsARowItCannotRead() {
		List<String> rows = new ArrayList<>(ROWS);
		rows.add("14 Sep 26 FOREIGN SHOP USD 10.00");

		assertThatThrownBy(() -> parser.parseText(statement(rows, SUMMARY_VALUES, BALANCES)))
			.isInstanceOf(StatementFormatException.class)
			.hasMessage("Could not read the transaction: 14 Sep 26 FOREIGN SHOP USD 10.00");
	}

	@Test
	void rejectsAStatementWithoutItsSummary() {
		List<String> lines = statement(ROWS, SUMMARY_VALUES, BALANCES).stream()
			.filter(line -> !line.equals(BALANCES))
			.toList();

		assertThatThrownBy(() -> parser.parseText(lines)).isInstanceOf(StatementFormatException.class)
			.hasMessage("Could not read the previous balance and total outstanding in the account summary");
	}

	@Test
	void rejectsAStatementFromAnotherIssuer() {
		List<String> lines = statement(ROWS, SUMMARY_VALUES, BALANCES).stream()
			.filter(line -> !line.contains("GSTIN of SBI Card"))
			.toList();

		assertThatThrownBy(() -> parser.parseText(lines)).isInstanceOf(StatementFormatException.class)
			.hasMessage("This doesn't look like an SBI credit card statement");
	}

	@Test
	void onlyThisParserRecognisesTheStatement() {
		List<String> lines = statement(ROWS, SUMMARY_VALUES, BALANCES);

		assertThat(parser.recognises(lines)).isTrue();
		assertThat(new HdfcSavingsStatementParser().recognises(lines)).isFalse();
		assertThat(new HdfcCreditCardStatementParser().recognises(lines)).isFalse();
		assertThat(new SbiSavingsStatementParser().recognises(lines)).isFalse();
	}

	@Test
	void readsTheSameStatementFromAPdf() throws IOException {
		byte[] pdf = TestPdfs.withLines(statement(ROWS, SUMMARY_VALUES, BALANCES));

		assertThat(parser.parseText(PdfText.lines(pdf, null)))
			.isEqualTo(parser.parseText(statement(ROWS, SUMMARY_VALUES, BALANCES)));
	}

	/** Places the rows after the account summary, in the order text extraction gives a real statement's boxes. */
	private static List<String> statement(List<String> rows, String summaryValues, String balances) {
		List<String> lines = new ArrayList<>();
		lines.add("Statement DateCash LimitCredit Limit");
		lines.add("Previous Balance Total Outstanding");
		lines.add("ACCOUNT SUMMARY");
		lines.add("for Statement Period: 10 Aug 26 to 09 Sep 26");
		lines.add("Date AmountTransaction Details");
		lines.add("GSTIN of SBI Card : 00TEST0000T0Z0                                  Stmt/Debit Note");
		lines.add("TEST USER");
		lines.add("769.00");
		lines.add("200.00");
		lines.add("1,00,000.00 30,000.00");
		lines.add("99,231.00 30,000.00");
		lines.add(summaryValues);
		lines.add("09 Sep 2026");
		lines.add("29 Sep 2026");
		lines.add(balances);
		lines.add("TRANSACTIONS FOR TEST USER");
		lines.addAll(rows);
		lines.add("Current Stmt Period");
		lines.add("REWARD SUMMARY");
		return lines;
	}

}
