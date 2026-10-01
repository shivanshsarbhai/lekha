package com.lekha.statements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import com.lekha.accounts.AccountType;
import com.lekha.accounts.Institution;
import com.lekha.transactions.PaymentMode;

/** All statement text here is made up, but follows the layout of a real HDFC statement exactly. */
class HdfcSavingsStatementParserTest {

	private static final String SUMMARY_VALUES = "10,000.00 2 2 950.00 1,50,999.00 1,60,049.00";

	private static final List<String> ROWS = List.of(
			"01/09/26 UPI-SWIGGY-SWIGGY@ICICI 0000111122223333 01/09/26 450.00 9,550.00",
			"05/09/26 NEFT CR-ACME CORP-SALARY SEP 0000000012345678 05/09/26 1,50,000.00 1,59,550.00",
			"06/09/26 FT- 1234567890-11111111111111 - ABC TRAD 0000001111111111 06/09/26 999.00 1,60,549.00",
			"ING LIMITED -",
			"07/09/26 UPI-JOHN DOE",
			"JOHN.DOE@OKBANK-",
			"0000222233334444 08/09/26 500.00 1,60,049.00",
			"OKBANK0000001-222233334444-DINNER");

	private final HdfcSavingsStatementParser parser = new HdfcSavingsStatementParser();

	@Test
	void supportsOnlyHdfcBankAccounts() {
		assertThat(parser.supports(Institution.HDFC, AccountType.BANK)).isTrue();
		assertThat(parser.supports(Institution.HDFC, AccountType.CREDIT_CARD)).isFalse();
		assertThat(parser.supports(Institution.SBI, AccountType.BANK)).isFalse();
	}

	@Test
	void readsEveryTransactionWithItsSignFromTheBalance() {
		List<StatementEntry> entries = parser.parseText(statement(ROWS, SUMMARY_VALUES));

		assertThat(entries)
				.extracting(StatementEntry::transactionDate, StatementEntry::amount, StatementEntry::balanceAfter,
						StatementEntry::paymentMode)
				.containsExactly(
						tuple(LocalDate.of(2026, 9, 1), new BigDecimal("-450.00"), new BigDecimal("9550.00"),
								PaymentMode.UPI),
						tuple(LocalDate.of(2026, 9, 5), new BigDecimal("150000.00"), new BigDecimal("159550.00"),
								PaymentMode.NEFT),
						tuple(LocalDate.of(2026, 9, 6), new BigDecimal("999.00"), new BigDecimal("160549.00"),
								PaymentMode.OTHER),
						tuple(LocalDate.of(2026, 9, 7), new BigDecimal("-500.00"), new BigDecimal("160049.00"),
								PaymentMode.UPI));
	}

	@Test
	void keepsTheReferenceSettlementDateAndOriginalLines() {
		StatementEntry upi = parser.parseText(statement(ROWS, SUMMARY_VALUES)).getLast();

		assertThat(upi.settlementDate()).isEqualTo(LocalDate.of(2026, 9, 8));
		assertThat(upi.description()).isEqualTo("UPI-JOHN DOE JOHN.DOE@OKBANK- OKBANK0000001-222233334444-DINNER");
		assertThat(upi.metadata()).isEqualTo(Map.of("reference", "0000222233334444", "rawNarration",
				"UPI-JOHN DOE\nJOHN.DOE@OKBANK-\nOKBANK0000001-222233334444-DINNER"));
	}

	@Test
	void joinsAWordCutAtTheFortyCharacterWrapWithoutASpace() {
		StatementEntry transfer = parser.parseText(statement(ROWS, SUMMARY_VALUES)).get(2);

		assertThat(transfer.description()).isEqualTo("FT- 1234567890-11111111111111 - ABC TRADING LIMITED -");
	}

	@Test
	void rejectsARowWhoseBalanceDoesNotAddUp() {
		List<String> rows = new ArrayList<>(ROWS);
		rows.set(0, "01/09/26 UPI-SWIGGY-SWIGGY@ICICI 0000111122223333 01/09/26 450.00 9,560.00");

		assertThatThrownBy(() -> parser.parseText(statement(rows, SUMMARY_VALUES)))
				.isInstanceOf(StatementFormatException.class)
				.hasMessage("Balance does not add up for the transaction on 2026-09-01: UPI-SWIGGY-SWIGGY@ICICI");
	}

	@Test
	void rejectsAStatementWhoseCountsDoNotMatchTheSummary() {
		assertThatThrownBy(
				() -> parser.parseText(statement(ROWS, "10,000.00 3 2 950.00 1,50,999.00 1,60,049.00")))
				.isInstanceOf(StatementFormatException.class)
				.hasMessage("Statement summary mismatch for Dr Count: the summary says 3 but the transactions give 2");
	}

	@Test
	void rejectsAStatementWhoseTotalsDoNotMatchTheSummary() {
		assertThatThrownBy(
				() -> parser.parseText(statement(ROWS, "10,000.00 2 2 951.00 1,50,999.00 1,60,049.00")))
				.isInstanceOf(StatementFormatException.class)
				.hasMessage(
						"Statement summary mismatch for Debits: the summary says 951.00 but the transactions give 950.00");
	}

	@Test
	void rejectsARowWithoutAnAmountLine() {
		assertThatThrownBy(() -> parser.parseText(statement(List.of("01/09/26 UPI-SWIGGY"), SUMMARY_VALUES)))
				.isInstanceOf(StatementFormatException.class)
				.hasMessage("No amount found for transaction starting: 01/09/26 UPI-SWIGGY");
	}

	@Test
	void rejectsTextThatIsNotAStatement() {
		assertThatThrownBy(() -> parser.parseText(List.of("Hello", "this is not a statement")))
				.isInstanceOf(StatementFormatException.class)
				.hasMessage("Could not find \"Date Narration\" in the statement");
	}

	@Test
	void rejectsAStatementThatContinuesOnAnotherPage() {
		List<String> rows = new ArrayList<>(ROWS);
		rows.add(2, "Date Narration Chq./Ref.No. Value Dt Withdrawal Amt. Deposit Amt. Closing Balance");

		assertThatThrownBy(() -> parser.parseText(statement(rows, SUMMARY_VALUES)))
				.isInstanceOf(StatementFormatException.class)
				.hasMessage("Multi-page statements are not supported yet");
	}

	@Test
	void readsTheSameStatementFromAPdf() throws IOException {
		byte[] pdf = pdfWith(statement(ROWS, SUMMARY_VALUES));

		assertThat(parser.parse(new ByteArrayInputStream(pdf)))
				.isEqualTo(parser.parseText(statement(ROWS, SUMMARY_VALUES)));
	}

	@Test
	void rejectsAFileThatIsNotAPdf() {
		assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream("not a pdf".getBytes())))
				.isInstanceOf(StatementFormatException.class)
				.hasMessage("Could not read the file as a PDF");
	}

	/** Wraps transaction lines in the header, summary and page text that surround them in a real statement. */
	private static List<String> statement(List<String> rows, String summaryValues) {
		List<String> lines = new ArrayList<>();
		lines.add("Date Narration Chq./Ref.No. Value Dt Withdrawal Amt. Deposit Amt. Closing Balance");
		lines.addAll(rows);
		lines.add("STATEMENT SUMMARY :-");
		lines.add("Opening Balance Dr Count Cr Count Debits Credits Closing Bal");
		lines.add(summaryValues);
		lines.add("Generated On: 01-Oct-2026 16:24 Generated By: 000000000 Requesting Branch Code: NET");
		lines.add("Page No .: 1");
		lines.add("MR TEST USER");
		lines.add("Account Branch : SOMEWHERE");
		return lines;
	}

	private static byte[] pdfWith(List<String> lines) throws IOException {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
				content.setLeading(10);
				content.newLineAtOffset(20, 760);
				for (String line : lines) {
					content.showText(line);
					content.newLine();
				}
				content.endText();
			}
			document.save(out);
			return out.toByteArray();
		}
	}

}
