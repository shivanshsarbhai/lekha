package com.lekha.statements;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.lekha.accounts.AccountType;
import com.lekha.accounts.Institution;
import com.lekha.transactions.PaymentMode;

/**
 * Reads HDFC savings account statement PDFs. The text extracted from them loses the empty withdrawal or deposit
 * column, so each transaction's direction is worked out from how the closing balance changed.
 */
@Component
class HdfcSavingsStatementParser implements StatementParser {

	/** HDFC wraps narrations at this many characters, cutting words if needed. */
	private static final int NARRATION_WIDTH = 40;

	private static final String TABLE_HEADER = "Date Narration";

	private static final String SUMMARY_HEADER = "STATEMENT SUMMARY";

	private static final Pattern ROW_START = Pattern.compile("^(\\d{2}/\\d{2}/\\d{2})\\s+(.*)$");

	private static final Pattern ROW_TAIL = Pattern
		.compile("^(.*?)\\s*(\\S+)\\s+(\\d{2}/\\d{2}/\\d{2})\\s+([\\d,]+\\.\\d{2})\\s+([\\d,]+\\.\\d{2})$");

	private static final Pattern SUMMARY_VALUES = Pattern.compile(
			"^([\\d,]+\\.\\d{2})\\s+(\\d+)\\s+(\\d+)\\s+([\\d,]+\\.\\d{2})\\s+([\\d,]+\\.\\d{2})\\s+([\\d,]+\\.\\d{2})$");

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yy");

	@Override
	public boolean supports(Institution institution, AccountType type) {
		return institution == Institution.HDFC && type == AccountType.BANK;
	}

	@Override
	public List<StatementEntry> parse(InputStream file) {
		return parseText(PdfText.lines(file));
	}

	List<StatementEntry> parseText(List<String> lines) {
		List<Row> rows = parseRows(lines);
		Summary summary = parseSummary(lines);
		List<StatementEntry> entries = toEntries(rows, summary.openingBalance());
		verify(entries, summary);
		return entries;
	}

	private static List<Row> parseRows(List<String> lines) {
		int start = indexOfLineStartingWith(lines, TABLE_HEADER);
		int end = indexOfLineStartingWith(lines, SUMMARY_HEADER);

		List<List<String>> blocks = new ArrayList<>();
		for (String raw : lines.subList(start + 1, end)) {
			String line = raw.strip();
			if (line.isEmpty()) {
				continue;
			}
			if (line.startsWith(TABLE_HEADER)) {
				throw new StatementFormatException("Multi-page statements are not supported yet");
			}
			if (ROW_START.matcher(line).matches()) {
				blocks.add(new ArrayList<>());
			}
			else if (blocks.isEmpty()) {
				throw new StatementFormatException("Expected a transaction date but found: " + line);
			}
			blocks.getLast().add(line);
		}
		return blocks.stream().map(HdfcSavingsStatementParser::toRow).toList();
	}

	private static Row toRow(List<String> block) {
		Matcher start = ROW_START.matcher(block.getFirst());
		start.matches();
		LocalDate date = parseDate(start.group(1));

		List<String> texts = new ArrayList<>();
		texts.add(start.group(2));
		texts.addAll(block.subList(1, block.size()));

		Matcher tail = null;
		List<String> narrationLines = new ArrayList<>();
		for (String text : texts) {
			Matcher candidate = ROW_TAIL.matcher(text);
			if (candidate.matches()) {
				if (tail != null) {
					throw new StatementFormatException("Two amount lines in one transaction on " + date);
				}
				tail = candidate;
				text = candidate.group(1).strip();
			}
			if (!text.isEmpty()) {
				narrationLines.add(text);
			}
		}
		if (tail == null) {
			throw new StatementFormatException("No amount found for transaction starting: " + block.getFirst());
		}
		if (narrationLines.isEmpty()) {
			throw new StatementFormatException("No narration found for transaction on " + date);
		}

		return new Row(date, List.copyOf(narrationLines), tail.group(2), parseDate(tail.group(3)),
				parseAmount(tail.group(4)), parseAmount(tail.group(5)));
	}

	private static Summary parseSummary(List<String> lines) {
		int start = indexOfLineStartingWith(lines, SUMMARY_HEADER);
		for (String raw : lines.subList(start + 1, lines.size())) {
			Matcher values = SUMMARY_VALUES.matcher(raw.strip());
			if (values.matches()) {
				return new Summary(parseAmount(values.group(1)), Integer.parseInt(values.group(2)),
						Integer.parseInt(values.group(3)), parseAmount(values.group(4)), parseAmount(values.group(5)),
						parseAmount(values.group(6)));
			}
		}
		throw new StatementFormatException("Could not read the statement summary");
	}

	private static List<StatementEntry> toEntries(List<Row> rows, BigDecimal openingBalance) {
		List<StatementEntry> entries = new ArrayList<>();
		BigDecimal balance = openingBalance;
		for (Row row : rows) {
			BigDecimal amount;
			if (balance.subtract(row.amount()).compareTo(row.closingBalance()) == 0) {
				amount = row.amount().negate();
			}
			else if (balance.add(row.amount()).compareTo(row.closingBalance()) == 0) {
				amount = row.amount();
			}
			else {
				throw new StatementFormatException(
						"Balance does not add up for the transaction on " + row.date() + ": " + row.narration());
			}
			entries.add(new StatementEntry(row.date(), row.valueDate(), row.narration(), amount, row.closingBalance(),
					paymentModeOf(row.narration()),
					Map.of("reference", row.reference(), "rawNarration", String.join("\n", row.narrationLines()))));
			balance = row.closingBalance();
		}
		return entries;
	}

	private static void verify(List<StatementEntry> entries, Summary summary) {
		Predicate<StatementEntry> isDebit = entry -> entry.amount().signum() < 0;
		List<StatementEntry> debits = entries.stream().filter(isDebit).toList();
		List<StatementEntry> credits = entries.stream().filter(isDebit.negate()).toList();

		check("Dr Count", summary.debitCount(), debits.size());
		check("Cr Count", summary.creditCount(), credits.size());
		check("Debits", summary.totalDebits(), sum(debits).negate());
		check("Credits", summary.totalCredits(), sum(credits));
		BigDecimal finalBalance = entries.isEmpty() ? summary.openingBalance() : entries.getLast().balanceAfter();
		check("Closing Bal", summary.closingBalance(), finalBalance);
	}

	private static void check(String field, int expected, int actual) {
		if (expected != actual) {
			throw mismatch(field, String.valueOf(expected), String.valueOf(actual));
		}
	}

	private static void check(String field, BigDecimal expected, BigDecimal actual) {
		if (expected.compareTo(actual) != 0) {
			throw mismatch(field, expected.toPlainString(), actual.toPlainString());
		}
	}

	private static StatementFormatException mismatch(String field, String expected, String actual) {
		return new StatementFormatException("Statement summary mismatch for " + field + ": the summary says "
				+ expected + " but the transactions give " + actual);
	}

	private static BigDecimal sum(List<StatementEntry> entries) {
		return entries.stream().map(StatementEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static PaymentMode paymentModeOf(String narration) {
		if (narration.startsWith("UPI-")) {
			return PaymentMode.UPI;
		}
		if (narration.startsWith("NEFT")) {
			return PaymentMode.NEFT;
		}
		if (narration.startsWith("IMPS")) {
			return PaymentMode.IMPS;
		}
		if (narration.startsWith("RTGS")) {
			return PaymentMode.RTGS;
		}
		if (narration.startsWith("POS ")) {
			return PaymentMode.CARD;
		}
		if (narration.startsWith("ATW-") || narration.startsWith("NWD-") || narration.startsWith("EAW-")) {
			return PaymentMode.ATM;
		}
		return PaymentMode.OTHER;
	}

	private static int indexOfLineStartingWith(List<String> lines, String prefix) {
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).strip().startsWith(prefix)) {
				return i;
			}
		}
		throw new StatementFormatException("Could not find \"" + prefix + "\" in the statement");
	}

	private static LocalDate parseDate(String text) {
		try {
			return LocalDate.parse(text, DATE);
		}
		catch (DateTimeParseException ex) {
			throw new StatementFormatException("Invalid date: " + text);
		}
	}

	private static BigDecimal parseAmount(String text) {
		return new BigDecimal(text.replace(",", ""));
	}

	/** One printed row. The amount is unsigned until the balance shows which way the money moved. */
	private record Row(LocalDate date, List<String> narrationLines, String reference, LocalDate valueDate,
			BigDecimal amount, BigDecimal closingBalance) {

		/** The narration as one string: lines cut mid-word are joined directly, others with a space. */
		String narration() {
			StringBuilder narration = new StringBuilder(narrationLines.getFirst());
			for (int i = 1; i < narrationLines.size(); i++) {
				if (narrationLines.get(i - 1).length() < NARRATION_WIDTH) {
					narration.append(' ');
				}
				narration.append(narrationLines.get(i));
			}
			return narration.toString();
		}

	}

	private record Summary(BigDecimal openingBalance, int debitCount, int creditCount, BigDecimal totalDebits,
			BigDecimal totalCredits, BigDecimal closingBalance) {
	}

}
