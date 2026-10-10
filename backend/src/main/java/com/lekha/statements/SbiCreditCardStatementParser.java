package com.lekha.statements;

import static com.lekha.statements.StatementChecks.check;
import static com.lekha.statements.StatementChecks.parseAmount;
import static com.lekha.statements.StatementChecks.sum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.lekha.accounts.AccountType;
import com.lekha.accounts.Institution;
import com.lekha.transactions.PaymentMode;

/**
 * Reads SBI Card credit card statement PDFs. Like other card statements there is no running balance, so the rows are
 * checked against the account summary: payments, purchases and fees, and the closing Total Outstanding.
 */
@Component
class SbiCreditCardStatementParser implements StatementParser {

	private static final String ISSUER = "GSTIN of SBI Card";

	private static final String TRANSACTIONS_HEADER = "TRANSACTIONS FOR";

	private static final String AMOUNT = "[\\d,]+\\.\\d{2}";

	private static final Pattern ROW_START = Pattern.compile("^\\d{2} [A-Z][a-z]{2} \\d{2}\\s.*");

	/** Date, the merchant, city and country columns, the amount, and D for a debit or C for a credit. */
	private static final Pattern ROW = Pattern
		.compile("^(\\d{2} [A-Z][a-z]{2} \\d{2})\\s+(.+?)\\s+(" + AMOUNT + ")\\s+([DC])$");

	/** Printed before the amount on purchases offered as EMIs. */
	private static final Pattern EMI_MARKER = Pattern.compile("\\s*\\(Pay in EMIs\\)\\s*");

	/** The columns of a row's details are separated by runs of spaces: merchant, city, country. */
	private static final Pattern COLUMN_GAP = Pattern.compile("\\s{2,}");

	private static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");

	/** Payments and credits, purchases and other debits, and fees, taxes and interest. */
	private static final Pattern SUMMARY_VALUES = Pattern
		.compile("^(" + AMOUNT + ")\\s+(" + AMOUNT + ")\\s+(" + AMOUNT + ")$");

	/**
	 * Text extraction runs Total Outstanding into Previous Balance, as in "1,862.000.43 CR". Either can be a credit
	 * balance, marked CR.
	 */
	private static final Pattern BALANCES = Pattern
		.compile("^(" + AMOUNT + ")(?:\\s*(CR|DR))?(" + AMOUNT + ")(?:\\s*(CR|DR))?$");

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yy", Locale.ENGLISH);

	@Override
	public boolean supports(Institution institution, AccountType type) {
		return institution == Institution.SBI && type == AccountType.CREDIT_CARD;
	}

	@Override
	public String accountKind() {
		return "an SBI credit card";
	}

	@Override
	public boolean recognises(List<String> lines) {
		return lines.stream().anyMatch(line -> line.contains(ISSUER));
	}

	@Override
	public List<StatementEntry> parseText(List<String> lines) {
		List<String> stripped = lines.stream().map(String::strip).toList();
		if (!recognises(stripped)) {
			throw new StatementFormatException("This doesn't look like an SBI credit card statement");
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
	 * Rows are picked out by their "dd Mon yy" start wherever they are, so add-on cards and later pages are included.
	 * A line that starts like a row but can't be read fails the import.
	 */
	private static StatementEntry toEntry(String line) {
		Matcher row = ROW.matcher(line);
		if (!row.matches()) {
			throw new StatementFormatException("Could not read the transaction: " + line);
		}
		String details = row.group(2);
		Map<String, String> metadata = new HashMap<>();
		metadata.put("rawNarration", details);
		Matcher emi = EMI_MARKER.matcher(details);
		if (emi.find()) {
			metadata.put("payInEmis", "true");
			details = emi.replaceAll(" ").strip();
		}

		List<String> columns = Arrays.asList(COLUMN_GAP.split(details));
		String description = String.join(" ", columns);
		if (columns.size() >= 3 && COUNTRY.matcher(columns.getLast()).matches()) {
			metadata.put("country", columns.getLast());
			metadata.put("city", columns.get(columns.size() - 2));
			description = String.join(" ", columns.subList(0, columns.size() - 2));
		}

		BigDecimal amount = parseAmount(row.group(3));
		return new StatementEntry(StatementChecks.parseDate(row.group(1), DATE), null, description,
				row.group(4).equals("D") ? amount.negate() : amount, null, PaymentMode.CARD, metadata);
	}

	/** The summary is printed once, above the first cardholder's transactions. */
	private static Summary parseSummary(List<String> lines) {
		int end = 0;
		while (end < lines.size() && !lines.get(end).startsWith(TRANSACTIONS_HEADER)) {
			end++;
		}
		if (end == lines.size()) {
			throw new StatementFormatException("Could not find \"" + TRANSACTIONS_HEADER + "\" in the statement");
		}
		List<String> header = lines.subList(0, end);
		Matcher values = firstMatch(header, SUMMARY_VALUES, "payments, purchases and fees");
		Matcher balances = firstMatch(header, BALANCES, "previous balance and total outstanding");
		return new Summary(signed(balances.group(3), balances.group(4)), parseAmount(values.group(1)),
				parseAmount(values.group(2)), parseAmount(values.group(3)),
				signed(balances.group(1), balances.group(2)));
	}

	private static Matcher firstMatch(List<String> lines, Pattern pattern, String what) {
		for (String line : lines) {
			Matcher matcher = pattern.matcher(line);
			if (matcher.matches()) {
				return matcher;
			}
		}
		throw new StatementFormatException("Could not read the " + what + " in the account summary");
	}

	/** A balance owed is positive; one in your favour, marked CR, is negative. */
	private static BigDecimal signed(String amount, String marker) {
		BigDecimal value = parseAmount(amount);
		return "CR".equals(marker) ? value.negate() : value;
	}

	/**
	 * Total Outstanding is usually printed rounded to the rupee, so the computed closing balance may match it exactly
	 * or once rounded.
	 */
	private static void verify(List<StatementEntry> entries, Summary summary) {
		Predicate<StatementEntry> isDebit = entry -> entry.amount().signum() < 0;
		BigDecimal debits = sum(entries.stream().filter(isDebit).toList()).negate();
		BigDecimal credits = sum(entries.stream().filter(isDebit.negate()).toList());

		check("Payments, Reversals & other Credits", summary.credits(), credits);
		check("Purchases & Other Debits plus Fee, Taxes & Interest Charges", summary.purchases().add(summary.fees()),
				debits);
		BigDecimal closing = summary.previousBalance().subtract(credits).add(debits);
		BigDecimal rounded = closing.setScale(0, RoundingMode.HALF_UP).setScale(2);
		if (summary.totalOutstanding().compareTo(closing) != 0) {
			check("Total Outstanding", summary.totalOutstanding(), rounded);
		}
	}

	private record Summary(BigDecimal previousBalance, BigDecimal credits, BigDecimal purchases, BigDecimal fees,
			BigDecimal totalOutstanding) {
	}

}
