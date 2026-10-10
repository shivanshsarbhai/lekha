package com.lekha.statements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.ByteArrayInputStream;
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

/** All statement text here is made up, but follows the layout of a real HDFC card statement as PDFBox extracts it. */
class HdfcCreditCardStatementParserTest {

	private static final List<String> FIRST_PAGE_ROWS = List.of(
			"01/09/2026| 10:15 ACME STOREMUMBAI  C 1,250.00 l",
			"02/09/2026| 20:30 TEST CAFEBANGALORE + 12  C 480.50 l",
			"03/09/2026| 00:00 10% CashBack +  C 20.00 l");

	private static final List<String> SECOND_PAGE_ROWS = List.of(
			"05/09/2026| 19:30 BPPY CC PAYMENT DP0000000000TEST (Ref# ST000000000000000000001) +  C 5,000.00 l",
			"06/09/2026| 00:00 TEST AIRLINEGURGAON (Ref# MT000000000000000000002) +  C 300.00 l",
			"07/09/2026| 00:00 ANNUAL FEE (Ref# ST000000000000000000003)  C 99.00 l",
			"07/09/2026| 00:00 IGST-TEST000000-RATE 18.0 -07 (Ref# ST000000000000000000003)  C 17.82 l");

	/** Debits of 1,847.32 and credits of 5,320.00 on top of 5,000.25 leave 1,527.57, printed as 1,528.00. */
	private static final String SUMMARY_VALUES = "C5,000.25 C5,320.00 C1,847.32 C0.00";

	private static final String TOTAL_DUE = "C1,528.00";

	private final HdfcCreditCardStatementParser parser = new HdfcCreditCardStatementParser();

	@Test
	void supportsOnlyHdfcCreditCards() {
		assertThat(parser.supports(Institution.HDFC, AccountType.CREDIT_CARD)).isTrue();
		assertThat(parser.supports(Institution.HDFC, AccountType.BANK)).isFalse();
		assertThat(parser.supports(Institution.SBI, AccountType.CREDIT_CARD)).isFalse();
	}

	@Test
	void readsEveryTransactionAcrossPagesWithCreditsMarkedByAPlus() {
		List<StatementEntry> entries = parser.parseText(statement(SUMMARY_VALUES, TOTAL_DUE));

		assertThat(entries)
			.extracting(StatementEntry::transactionDate, StatementEntry::amount, StatementEntry::settlementDate,
					StatementEntry::balanceAfter, StatementEntry::paymentMode)
			.containsExactly(tuple(LocalDate.of(2026, 9, 1), new BigDecimal("-1250.00"), null, null, PaymentMode.CARD),
					tuple(LocalDate.of(2026, 9, 2), new BigDecimal("-480.50"), null, null, PaymentMode.CARD),
					tuple(LocalDate.of(2026, 9, 3), new BigDecimal("20.00"), null, null, PaymentMode.CARD),
					tuple(LocalDate.of(2026, 9, 5), new BigDecimal("5000.00"), null, null, PaymentMode.CARD),
					tuple(LocalDate.of(2026, 9, 6), new BigDecimal("300.00"), null, null, PaymentMode.CARD),
					tuple(LocalDate.of(2026, 9, 7), new BigDecimal("-99.00"), null, null, PaymentMode.CARD),
					tuple(LocalDate.of(2026, 9, 7), new BigDecimal("-17.82"), null, null, PaymentMode.CARD));
	}

	@Test
	void dropsTheReferenceAndRewardPointsFromTheDescription() {
		List<StatementEntry> entries = parser.parseText(statement(SUMMARY_VALUES, TOTAL_DUE));

		assertThat(entries).extracting(StatementEntry::description)
			.containsExactly("ACME STOREMUMBAI", "TEST CAFEBANGALORE", "10% CashBack",
					"BPPY CC PAYMENT DP0000000000TEST", "TEST AIRLINEGURGAON", "ANNUAL FEE",
					"IGST-TEST000000-RATE 18.0 -07");
	}

	@Test
	void keepsTheReferenceTimeAndRewardPointsInMetadata() {
		List<StatementEntry> entries = parser.parseText(statement(SUMMARY_VALUES, TOTAL_DUE));

		assertThat(entries.get(1).metadata()).isEqualTo(
				Map.of("rawNarration", "TEST CAFEBANGALORE", "time", "20:30", "rewardPoints", "12"));
		assertThat(entries.get(3).metadata()).isEqualTo(Map.of("rawNarration",
				"BPPY CC PAYMENT DP0000000000TEST (Ref# ST000000000000000000001)", "time", "19:30", "reference",
				"ST000000000000000000001"));
	}

	@Test
	void countsFinanceChargesAsDebits() {
		List<StatementEntry> entries = parser.parseText(statement("C5,000.25 C5,320.00 C1,747.32 C100.00", TOTAL_DUE));

		assertThat(entries).hasSize(7);
	}

	@Test
	void rejectsCreditsThatDoNotMatchTheSummary() {
		assertThatThrownBy(() -> parser.parseText(statement("C5,000.25 C5,300.00 C1,847.32 C0.00", TOTAL_DUE)))
			.isInstanceOf(StatementFormatException.class)
			.hasMessage("Statement summary mismatch for Payments/Credits: the summary says 5300.00 but the "
					+ "transactions give 5320.00");
	}

	@Test
	void rejectsDebitsThatDoNotMatchTheSummary() {
		assertThatThrownBy(() -> parser.parseText(statement("C5,000.25 C5,320.00 C1,850.00 C0.00", TOTAL_DUE)))
			.isInstanceOf(StatementFormatException.class)
			.hasMessage("Statement summary mismatch for Purchases/Debit: the summary says 1850.00 but the "
					+ "transactions give 1847.32");
	}

	@Test
	void rejectsATotalDueThatIsNotTheClosingAmountRoundedToTheRupee() {
		assertThatThrownBy(() -> parser.parseText(statement(SUMMARY_VALUES, "C1,527.00")))
			.isInstanceOf(StatementFormatException.class)
			.hasMessage("Statement summary mismatch for Total Amount Due: the summary says 1527.00 but the "
					+ "transactions give 1528.00");
	}

	@Test
	void rejectsARowItCannotRead() {
		List<String> lines = new ArrayList<>(statement(SUMMARY_VALUES, TOTAL_DUE));
		lines.add(lines.indexOf(FIRST_PAGE_ROWS.getFirst()), "08/09/2026| 12:00 FOREIGN SHOP USD 10.00");

		assertThatThrownBy(() -> parser.parseText(lines)).isInstanceOf(StatementFormatException.class)
			.hasMessage("Could not read the transaction: 08/09/2026| 12:00 FOREIGN SHOP USD 10.00");
	}

	@Test
	void rejectsAStatementWithoutASummary() {
		List<String> lines = statement(SUMMARY_VALUES, TOTAL_DUE).stream()
			.filter(line -> !line.equals("FINANCE CHARGES"))
			.toList();

		assertThatThrownBy(() -> parser.parseText(lines)).isInstanceOf(StatementFormatException.class)
			.hasMessage("Could not find \"FINANCE CHARGES\" in the statement");
	}

	@Test
	void rejectsAStatementFromAnotherIssuer() {
		List<String> lines = statement(SUMMARY_VALUES, TOTAL_DUE).stream()
			.filter(line -> !line.contains("HDFC Bank Credit Cards"))
			.toList();

		assertThatThrownBy(() -> parser.parseText(lines)).isInstanceOf(StatementFormatException.class)
			.hasMessage("This doesn't look like an HDFC credit card statement");
	}

	@Test
	void readsTheSameStatementFromAPdf() throws IOException {
		byte[] pdf = TestPdfs.withLines(statement(SUMMARY_VALUES, TOTAL_DUE));

		assertThat(parser.parse(new ByteArrayInputStream(pdf)))
			.isEqualTo(parser.parseText(statement(SUMMARY_VALUES, TOTAL_DUE)));
	}

	/** Places the rows among the boxes that surround them in a real statement, including a cashback summary. */
	private static List<String> statement(String summaryValues, String totalDue) {
		List<String> lines = new ArrayList<>();
		lines.add("TOTAL AMOUNT DUE");
		lines.add(totalDue);
		lines.add("MINIMUM DUE");
		lines.add("C200.00");
		lines.add("Domestic Transactions");
		lines.add("DATE & TIME TRANSACTION DESCRIPTION REWARDS AMOUNT PI");
		lines.add("TEST USER [CKYC ID : 00000000000000 ]");
		lines.addAll(FIRST_PAGE_ROWS);
		lines.add("Page 1 of 2");
		lines.add("PREVIOUS STATEMENT DUES");
		lines.add("PAYMENTS/CREDITS");
		lines.add("RECEIVED");
		lines.add("PURCHASES/DEBIT");
		lines.add("(Current Billing Cycle)");
		lines.add("FINANCE CHARGES");
		lines.add(summaryValues);
		lines.add("TOTAL CREDIT LIMIT");
		lines.add("Test Credit Card Statement");
		lines.add("HSN Code: 000000        HDFC Bank Credit Cards GSTIN: 00TEST0000T0Z0");
		lines.add("Domestic Transactions");
		lines.add("DATE & TIME TRANSACTION DESCRIPTION REWARDS AMOUNT PI");
		lines.addAll(SECOND_PAGE_ROWS);
		lines.add("Cash Back Summary");
		lines.add("SR NO. TRANSACTION AMOUNT");
		lines.add("1 10% CashBack C 20.00");
		lines.add("Page 2 of 2");
		return lines;
	}

}
