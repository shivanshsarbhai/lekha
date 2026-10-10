package com.lekha.statements;

import static com.lekha.statements.StatementChecks.check;
import static com.lekha.statements.StatementChecks.parseAmount;
import static com.lekha.statements.StatementChecks.sum;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.lekha.accounts.AccountType;
import com.lekha.accounts.Institution;
import com.lekha.transactions.PaymentMode;

/**
 * Reads SBI savings account statement PDFs. Each row has separate debit and credit columns, so the direction is
 * read directly; the running balance is still checked on every row to catch a misread column.
 */
@Component
class SbiSavingsStatementParser implements StatementParser {

	private static final String BANK_NAME = "State Bank of India";

	/** The only column heading that survives text extraction; it is repeated at the top of every page. */
	private static final String TABLE_HEADER = "Balance";

	private static final String SUMMARY_HEADER = "Statement Summary";

	private static final String AMOUNT = "[\\d,]+\\.\\d{2}";

	private static final Pattern ROW_START = Pattern
		.compile("^(\\d{2}/\\d{2}/\\d{4})\\s+(\\d{2}/\\d{2}/\\d{4})\\s*(.*)$");

	/** Reference, debit, credit and balance. An empty debit or credit is printed as "-". */
	private static final Pattern ROW_TAIL = Pattern.compile("^(?:(.*?)\\s+)?(\\S+)\\s+(-|" + AMOUNT + ")\\s+(-|"
			+ AMOUNT + ")\\s+(" + AMOUNT + ")(CR|DR)?$");

	private static final Pattern PAGE_FOOTER = Pattern.compile("^\\d+\\s*Page no\\.$");

	/** Text extraction runs the closing balance into the debit count, as in "38,032.66CR133 39". */
	private static final Pattern SUMMARY_VALUES = Pattern.compile("^(" + AMOUNT + ")(CR|DR)\\s+(" + AMOUNT + ")\\s+("
			+ AMOUNT + ")\\s+(" + AMOUNT + ")(CR|DR)\\s*(\\d+)\\s+(\\d+)$");

	private static final Pattern TRANSFER_PREFIX = Pattern.compile("^(?:WDL|DEP) TFR\\b\\s*");

	/** The branch printed after every narration, such as "0097692162094 AT 10528 SOME BRANCH". */
	private static final Pattern BRANCH_SUFFIX = Pattern.compile("\\s+(?:\\d{13}\\s+)?AT\\s+\\d{5}\\b.*$");

	private static final Pattern REFERENCE = Pattern.compile("^(?:UPI/(?:DR|CR)|IMPS)/(\\d+)/");

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	@Override
	public boolean supports(Institution institution, AccountType type) {
		return institution == Institution.SBI && type == AccountType.BANK;
	}

	@Override
	public List<StatementEntry> parse(InputStream file) {
		return parseText(PdfText.lines(file));
	}

	List<StatementEntry> parseText(List<String> lines) {
		List<String> stripped = lines.stream().map(String::strip).toList();
		if (stripped.stream().noneMatch(line -> line.contains(BANK_NAME))) {
			throw new StatementFormatException("This doesn't look like an SBI account statement");
		}
		int start = indexOf(stripped, TABLE_HEADER::equals, TABLE_HEADER);
		int end = indexOf(stripped, line -> line.startsWith(SUMMARY_HEADER), SUMMARY_HEADER);
		if (end < start) {
			throw new StatementFormatException("The statement summary comes before the transactions");
		}

		List<Row> rows = parseRows(stripped.subList(start + 1, end));
		Summary summary = parseSummary(stripped.subList(end + 1, stripped.size()));
		List<StatementEntry> entries = toEntries(rows, summary.openingBalance());
		verify(entries, summary);
		return entries;
	}

	private static List<Row> parseRows(List<String> lines) {
		List<List<String>> blocks = new ArrayList<>();
		for (String line : lines) {
			if (line.isEmpty() || line.equals(TABLE_HEADER) || PAGE_FOOTER.matcher(line).matches()) {
				continue;
			}
			if (ROW_START.matcher(line).matches()) {
				blocks.add(new ArrayList<>());
			}
			else if (blocks.isEmpty()) {
				throw new StatementFormatException("Expected a transaction date but found: " + line);
			}
			blocks.getLast().add(line);
		}
		return blocks.stream().map(SbiSavingsStatementParser::toRow).toList();
	}

	private static Row toRow(List<String> block) {
		Matcher start = ROW_START.matcher(block.getFirst());
		start.matches();
		LocalDate date = parseDate(start.group(1));

		List<String> texts = new ArrayList<>();
		if (!start.group(3).isEmpty()) {
			texts.add(start.group(3));
		}
		texts.addAll(block.subList(1, block.size()));

		List<String> narrationLines = new ArrayList<>();
		Matcher tail = null;
		for (String text : texts) {
			if (tail != null) {
				throw new StatementFormatException("Unexpected text after the amounts on " + date + ": " + text);
			}
			Matcher candidate = ROW_TAIL.matcher(text);
			if (candidate.matches()) {
				tail = candidate;
				text = candidate.group(1) == null ? "" : candidate.group(1);
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

		BigDecimal amount = amountOf(tail.group(3), tail.group(4), date);
		BigDecimal balance = signed(parseAmount(tail.group(5)), tail.group(6));
		return new Row(date, parseDate(start.group(2)), List.copyOf(narrationLines), tail.group(2), amount, balance);
	}

	private static BigDecimal amountOf(String debit, String credit, LocalDate date) {
		boolean isDebit = !debit.equals("-");
		boolean isCredit = !credit.equals("-");
		if (isDebit == isCredit) {
			throw new StatementFormatException("Expected either a debit or a credit for the transaction on " + date);
		}
		return isDebit ? parseAmount(debit).negate() : parseAmount(credit);
	}

	private static Summary parseSummary(List<String> lines) {
		for (String line : lines) {
			Matcher values = SUMMARY_VALUES.matcher(line);
			if (values.matches()) {
				return new Summary(signed(parseAmount(values.group(1)), values.group(2)),
						Integer.parseInt(values.group(7)), Integer.parseInt(values.group(8)),
						parseAmount(values.group(3)), parseAmount(values.group(4)),
						signed(parseAmount(values.group(5)), values.group(6)));
			}
		}
		throw new StatementFormatException("Could not read the statement summary");
	}

	private static List<StatementEntry> toEntries(List<Row> rows, BigDecimal openingBalance) {
		List<StatementEntry> entries = new ArrayList<>();
		BigDecimal balance = openingBalance;
		for (Row row : rows) {
			String description = row.description();
			if (balance.add(row.amount()).compareTo(row.balance()) != 0) {
				throw new StatementFormatException(
						"Balance does not add up for the transaction on " + row.date() + ": " + description);
			}
			Map<String, String> metadata = new HashMap<>();
			metadata.put("rawNarration", String.join("\n", row.narrationLines()));
			String reference = referenceOf(description, row.reference());
			if (reference != null) {
				metadata.put("reference", reference);
			}
			entries.add(new StatementEntry(row.date(), row.valueDate(), description, row.amount(), row.balance(),
					paymentModeOf(description), metadata));
			balance = row.balance();
		}
		return entries;
	}

	private static void verify(List<StatementEntry> entries, Summary summary) {
		Predicate<StatementEntry> isDebit = entry -> entry.amount().signum() < 0;
		List<StatementEntry> debits = entries.stream().filter(isDebit).toList();
		List<StatementEntry> credits = entries.stream().filter(isDebit.negate()).toList();

		check("Dr Count", summary.debitCount(), debits.size());
		check("Cr Count", summary.creditCount(), credits.size());
		check("Total Debits", summary.totalDebits(), sum(debits).negate());
		check("Total Credits", summary.totalCredits(), sum(credits));
		BigDecimal finalBalance = entries.isEmpty() ? summary.openingBalance() : entries.getLast().balanceAfter();
		check("Closing Balance", summary.closingBalance(), finalBalance);
	}

	/** The UPI or IMPS reference from the narration, since SBI's own reference column is usually "-". */
	private static @Nullable String referenceOf(String description, String referenceColumn) {
		Matcher reference = REFERENCE.matcher(description);
		if (reference.find()) {
			return reference.group(1);
		}
		return referenceColumn.equals("-") ? null : referenceColumn;
	}

	private static PaymentMode paymentModeOf(String description) {
		if (description.startsWith("UPI/")) {
			return PaymentMode.UPI;
		}
		if (description.startsWith("IMPS")) {
			return PaymentMode.IMPS;
		}
		if (description.startsWith("NEFT")) {
			return PaymentMode.NEFT;
		}
		if (description.startsWith("RTGS")) {
			return PaymentMode.RTGS;
		}
		return PaymentMode.OTHER;
	}

	private static BigDecimal signed(BigDecimal amount, @Nullable String crOrDr) {
		return "DR".equals(crOrDr) ? amount.negate() : amount;
	}

	private static int indexOf(List<String> lines, Predicate<String> matches, String label) {
		for (int i = 0; i < lines.size(); i++) {
			if (matches.test(lines.get(i))) {
				return i;
			}
		}
		throw new StatementFormatException("Could not find \"" + label + "\" in the statement");
	}

	private static LocalDate parseDate(String text) {
		return StatementChecks.parseDate(text, DATE);
	}

	/** One printed row. The amount is already signed: negative for a debit, positive for a credit. */
	private record Row(LocalDate date, LocalDate valueDate, List<String> narrationLines, String reference,
			BigDecimal amount, BigDecimal balance) {

		/** The narration without the "WDL TFR" prefix or the branch suffix, with wrapped lines rejoined. */
		String description() {
			String joined = joinWrapped(narrationLines);
			String trimmed = BRANCH_SUFFIX.matcher(TRANSFER_PREFIX.matcher(joined).replaceFirst("")).replaceFirst("");
			return trimmed.isBlank() ? joined : trimmed;
		}

		/**
		 * SBI wraps narrations by printed width, often mid-word. In a UPI narration
		 * ("UPI/DR/ref/name/bank/upi-id/note") only the name and note can contain spaces, so a break anywhere else
		 * is joined directly. Elsewhere a break after "/" or "-" is joined directly too.
		 */
		private static String joinWrapped(List<String> lines) {
			List<String> parts = new ArrayList<>(lines);
			if (TRANSFER_PREFIX.matcher(parts.getFirst()).replaceFirst("").isEmpty() && parts.size() > 1) {
				parts.removeFirst();
			}
			StringBuilder text = new StringBuilder();
			for (String part : parts) {
				if (!text.isEmpty() && needsSpace(text, part)) {
					text.append(' ');
				}
				text.append(part);
			}
			return text.toString().replaceAll("\\s+", " ");
		}

		private static boolean needsSpace(CharSequence before, String next) {
			char last = before.charAt(before.length() - 1);
			if (last == '/' || last == '-' || next.startsWith("/")) {
				return false;
			}
			String text = TRANSFER_PREFIX.matcher(before).replaceFirst("");
			if (!text.startsWith("UPI/")) {
				return true;
			}
			long field = text.chars().filter(c -> c == '/').count();
			return field == 3 || field >= 6;
		}

	}

	private record Summary(BigDecimal openingBalance, int debitCount, int creditCount, BigDecimal totalDebits,
			BigDecimal totalCredits, BigDecimal closingBalance) {
	}

}
