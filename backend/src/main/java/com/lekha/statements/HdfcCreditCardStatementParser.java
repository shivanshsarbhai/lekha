package com.lekha.statements;

import static com.lekha.statements.StatementChecks.check;
import static com.lekha.statements.StatementChecks.parseAmount;
import static com.lekha.statements.StatementChecks.sum;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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
 * Reads HDFC credit card statement PDFs. Every HDFC card (Regalia Gold, Swiggy and others) prints the same template,
 * so one parser serves them all. A card statement has no running balance; instead the debits and credits are checked
 * against the statement summary, and the closing amount against the Total Amount Due.
 */
@Component
class HdfcCreditCardStatementParser implements StatementParser {

	private static final String ISSUER = "HDFC Bank Credit Cards";

	/** PDFBox extracts the rupee sign in HDFC's font as the letter "C". */
	private static final String AMOUNT = "[\\d,]+\\.\\d{2}";

	private static final Pattern ROW_START = Pattern.compile("^\\d{2}/\\d{2}/\\d{4}\\|.*");

	/**
	 * Date, time, description, optional reward points, an optional "+" marking a credit, and the amount. The trailing
	 * "l" is the bullet printed in the PI column.
	 */
	private static final Pattern ROW = Pattern.compile("^(\\d{2}/\\d{2}/\\d{4})\\|\\s*(\\d{2}:\\d{2})\\s+(.+?)"
			+ "(?:\\s+([+-])\\s*(\\d+))?\\s+(\\+\\s+)?C\\s*(" + AMOUNT + ")(?:\\s+l)?$");

	private static final Pattern REFERENCE = Pattern.compile("\\s*\\(Ref#\\s*([^)]+)\\)");

	private static final String SUMMARY_HEADER = "FINANCE CHARGES";

	/** Previous statement dues, payments/credits, purchases/debits and finance charges. */
	private static final Pattern SUMMARY_VALUES = Pattern
		.compile("^C(" + AMOUNT + ")\\s+C(" + AMOUNT + ")\\s+C(" + AMOUNT + ")\\s+C(" + AMOUNT + ")$");

	private static final String TOTAL_DUE_HEADER = "TOTAL AMOUNT DUE";

	private static final Pattern TOTAL_DUE = Pattern.compile("^C(" + AMOUNT + ")$");

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	@Override
	public boolean supports(Institution institution, AccountType type) {
		return institution == Institution.HDFC && type == AccountType.CREDIT_CARD;
	}

	@Override
	public List<StatementEntry> parse(InputStream file) {
		return parseText(PdfText.lines(file));
	}

	List<StatementEntry> parseText(List<String> lines) {
		List<String> stripped = lines.stream().map(String::strip).toList();
		if (stripped.stream().noneMatch(line -> line.contains(ISSUER))) {
			throw new StatementFormatException("This doesn't look like an HDFC credit card statement");
		}
		List<StatementEntry> entries = new ArrayList<>();
		for (String line : stripped) {
			if (ROW_START.matcher(line).matches()) {
				entries.add(toEntry(line));
			}
		}
		Summary summary = parseSummary(stripped);
		verify(entries, summary);
		return entries;
	}

	/**
	 * Rows sit between unrelated boxes on every page, so they are picked out by their "date|" start. A line that starts
	 * like a row but can't be read fails the import, rather than being skipped and caught only by the totals.
	 */
	private static StatementEntry toEntry(String line) {
		Matcher row = ROW.matcher(line);
		if (!row.matches()) {
			throw new StatementFormatException("Could not read the transaction: " + line);
		}
		String narration = row.group(3);
		Map<String, String> metadata = new HashMap<>();
		metadata.put("rawNarration", narration);
		metadata.put("time", row.group(2));
		if (row.group(5) != null) {
			metadata.put("rewardPoints", (row.group(4).equals("-") ? "-" : "") + row.group(5));
		}
		Matcher reference = REFERENCE.matcher(narration);
		if (reference.find()) {
			metadata.put("reference", reference.group(1).strip());
		}
		String description = REFERENCE.matcher(narration).replaceAll("").replaceAll("\\s+", " ").strip();
		BigDecimal amount = parseAmount(row.group(7));
		return new StatementEntry(StatementChecks.parseDate(row.group(1), DATE), null,
				description.isEmpty() ? narration : description, row.group(6) == null ? amount.negate() : amount, null,
				PaymentMode.CARD, metadata);
	}

	private static Summary parseSummary(List<String> lines) {
		Matcher values = valueAfter(lines, SUMMARY_HEADER, SUMMARY_VALUES);
		Matcher totalDue = valueAfter(lines, TOTAL_DUE_HEADER, TOTAL_DUE);
		return new Summary(parseAmount(values.group(1)), parseAmount(values.group(2)), parseAmount(values.group(3)),
				parseAmount(values.group(4)), parseAmount(totalDue.group(1)));
	}

	/** The line right after a heading, which is where the template prints that heading's values. */
	private static Matcher valueAfter(List<String> lines, String header, Pattern value) {
		int index = lines.indexOf(header);
		if (index < 0 || index + 1 >= lines.size()) {
			throw new StatementFormatException("Could not find \"" + header + "\" in the statement");
		}
		Matcher matcher = value.matcher(lines.get(index + 1));
		if (!matcher.matches()) {
			throw new StatementFormatException("Could not read the value under \"" + header + "\"");
		}
		return matcher;
	}

	/** The Total Amount Due is printed rounded to the rupee, so the computed closing amount is rounded the same way. */
	private static void verify(List<StatementEntry> entries, Summary summary) {
		Predicate<StatementEntry> isDebit = entry -> entry.amount().signum() < 0;
		BigDecimal debits = sum(entries.stream().filter(isDebit).toList()).negate();
		BigDecimal credits = sum(entries.stream().filter(isDebit.negate()).toList());

		check("Payments/Credits", summary.credits(), credits);
		check("Purchases/Debit", summary.purchases().add(summary.financeCharges()), debits);
		BigDecimal closing = summary.previousDues().subtract(credits).add(debits);
		check("Total Amount Due", summary.totalDue(), closing.setScale(0, RoundingMode.HALF_UP).setScale(2));
	}

	private record Summary(BigDecimal previousDues, BigDecimal credits, BigDecimal purchases,
			BigDecimal financeCharges, BigDecimal totalDue) {
	}

}
