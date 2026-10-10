package com.lekha.transactions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lekha.accounts.AccountType;
import com.lekha.categories.Category;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * One request to the language model: classify these transactions into one of these numbered options. The model only
 * ever answers with option numbers, and the JSON schema limits those to numbers that exist, so it can't invent a
 * category. {@link #parse} checks every answer again anyway and drops the ones that don't fit.
 * <p>
 * The options go in the system message and the transactions in the user message. The system message is the same for
 * every batch, which lets the model server reuse its work on it between requests.
 */
final class ClassificationPrompt {

	private static final Logger log = LoggerFactory.getLogger(ClassificationPrompt.class);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final Map<AllocationKind, String> KIND_NAMES = Map.of(AllocationKind.EXPENSE, "Expense",
			AllocationKind.INCOME, "Income", AllocationKind.TRANSFER, "Transfer", AllocationKind.INVESTMENT,
			"Investment", AllocationKind.LENT, "Lent");

	private static final String INSTRUCTIONS = """
			You classify the bank and credit card transactions of one person in India. For each transaction, choose \
			the single option below that best describes what the money was for.

			Kinds:
			- Expense: money spent on themselves or their household. Money coming back for a purchase (a refund) \
			is an Expense in the purchase's category.
			- Income: money earned or received to keep: salary, interest, cashback, dividends, gifts.
			- Transfer: money moving between their own accounts, such as paying their own credit card bill or moving \
			savings. It is not spending.
			- Investment: money put into or taken out of investments: mutual funds, stocks, fixed deposits, PPF, \
			NPS. Redemptions are Investment too.
			- Lent: money paid for someone else that they will pay back.

			Rules:
			- "out" means money left the account, "in" means it came in.
			- Their past choices for similar transactions show how this person classifies things. When the merchant \
			matches, follow them, even if you would have chosen differently.
			- UPI and card narrations usually contain the merchant's name, e.g. "UPI-SWIGGY-..." is Swiggy.
			- If unsure, still choose the most likely option, and say what you are unsure about.
			- The reason is one short sentence of at most 15 words naming the clue you used.

			Options:
			""";

	/** One thing the model can pick: a kind and, except for money lent, a category. */
	record Choice(int number, AllocationKind kind, @Nullable UUID categoryId, String label) {
	}

	/** A past transaction the person classified as one piece, shown to the model as an example. */
	record Example(String description, BigDecimal amount, AllocationKind kind, @Nullable UUID categoryId) {
	}

	/** A transaction to classify, with its most similar examples. */
	record Item(UUID transactionId, String description, BigDecimal amount, AccountType accountType,
			List<Example> examples) {
	}

	private final List<Choice> choices;

	private final List<Item> items;

	private final String model;

	ClassificationPrompt(List<Choice> choices, List<Item> items, String model) {
		if (items.isEmpty()) {
			throw new IllegalArgumentException("A prompt needs at least one transaction");
		}
		this.choices = List.copyOf(choices);
		this.items = List.copyOf(items);
		this.model = model;
	}

	/**
	 * Numbers every category, grouped by kind as the categories page shows them: each top-level category followed by
	 * its sub-categories, then one option for money lent, which has no categories.
	 */
	static List<Choice> choicesFrom(List<Category> categories) {
		Map<UUID, List<Category>> children = categories.stream()
			.filter(category -> category.parentId() != null)
			.collect(Collectors.groupingBy(Category::parentId));
		Comparator<Category> byName = Comparator.comparing(category -> category.name().toLowerCase());

		List<Choice> choices = new ArrayList<>();
		for (AllocationKind kind : List.of(AllocationKind.EXPENSE, AllocationKind.INCOME, AllocationKind.TRANSFER,
				AllocationKind.INVESTMENT)) {
			List<Category> parents = categories.stream()
				.filter(category -> category.parentId() == null && category.kind().name().equals(kind.name()))
				.sorted(byName)
				.toList();
			for (Category parent : parents) {
				choices.add(new Choice(choices.size() + 1, kind, parent.id(), KIND_NAMES.get(kind) + ": " + parent.name()));
				for (Category child : children.getOrDefault(parent.id(), List.of()).stream().sorted(byName).toList()) {
					choices.add(new Choice(choices.size() + 1, kind, child.id(),
							KIND_NAMES.get(kind) + ": " + parent.name() + " › " + child.name()));
				}
			}
		}
		choices.add(new Choice(choices.size() + 1, AllocationKind.LENT, null, "Lent: paid for someone else"));
		return choices;
	}

	/** How a kind and category read in the options, e.g. "Expense: Food › Ordering in". */
	static String labelOf(List<Choice> choices, AllocationKind kind, @Nullable UUID categoryId) {
		return choices.stream()
			.filter(choice -> choice.kind() == kind && Objects.equals(choice.categoryId(), categoryId))
			.map(Choice::label)
			.findFirst()
			.orElse(KIND_NAMES.get(kind));
	}

	String system() {
		return INSTRUCTIONS + choices.stream()
			.map(choice -> choice.number() + " = " + choice.label())
			.collect(Collectors.joining("\n"));
	}

	String user() {
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < items.size(); i++) {
			Item item = items.get(i);
			text.append("Transaction ").append(i + 1).append(": ").append(describe(item.description(), item.amount()))
				.append(" · ").append(item.accountType() == AccountType.CREDIT_CARD ? "credit card" : "bank account")
				.append('\n');
			List<String> examples = item.examples()
				.stream()
				.map(example -> optionFor(example.kind(), example.categoryId())
					.map(choice -> "  - " + describe(example.description(), example.amount()) + " → option "
							+ choice.number() + " (" + choice.label() + ")")
					.orElse(null))
				.filter(Objects::nonNull)
				.toList();
			if (examples.isEmpty()) {
				text.append("  They haven't classified anything similar.\n");
			}
			else {
				text.append("  Their past choices for similar transactions:\n");
				examples.forEach(line -> text.append(line).append('\n'));
			}
		}
		text.append("\nAnswer for every transaction, 1 to ").append(items.size()).append('.');
		return text.toString();
	}

	/**
	 * Limits the reply to one answer per transaction, each naming a transaction and an option that exist. The reason
	 * comes before the option so the model writes down its clue before committing to a choice.
	 */
	Map<String, Object> schema() {
		Map<String, Object> answer = new LinkedHashMap<>();
		answer.put("type", "object");
		answer.put("properties", orderedMap("transaction", Map.of("type", "integer", "enum", numbers(items.size())),
				"reason", Map.of("type", "string"), "option", Map.of("type", "integer", "enum", numbers(choices.size()))));
		answer.put("required", List.of("transaction", "reason", "option"));

		Map<String, Object> answers = new LinkedHashMap<>();
		answers.put("type", "array");
		answers.put("items", answer);
		answers.put("minItems", items.size());
		answers.put("maxItems", items.size());

		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of("answers", answers));
		schema.put("required", List.of("answers"));
		return schema;
	}

	/**
	 * The model's answers as suggestions, keyed by transaction. Answers naming an unknown transaction or option are
	 * dropped, as is a second answer for the same transaction. A reply that isn't JSON gives no suggestions.
	 */
	Map<UUID, AiSuggestion> parse(String reply) {
		JsonNode root;
		try {
			root = JSON.readTree(reply);
		}
		catch (JacksonException ex) {
			log.warn("The model's reply wasn't JSON: {}", ex.getOriginalMessage());
			return Map.of();
		}

		Map<UUID, AiSuggestion> suggestions = new LinkedHashMap<>();
		Set<Integer> answered = new HashSet<>();
		for (JsonNode answer : root.path("answers")) {
			int transaction = answer.path("transaction").asInt(0);
			int option = answer.path("option").asInt(0);
			if (transaction < 1 || transaction > items.size() || option < 1 || option > choices.size()
					|| !answered.add(transaction)) {
				continue;
			}
			Choice choice = choices.get(option - 1);
			UUID transactionId = items.get(transaction - 1).transactionId();
			suggestions.put(transactionId, new AiSuggestion(transactionId, choice.kind(), choice.categoryId(),
					reason(answer.path("reason").asString("")), model));
		}
		return suggestions;
	}

	private Optional<Choice> optionFor(AllocationKind kind, @Nullable UUID categoryId) {
		return choices.stream()
			.filter(choice -> choice.kind() == kind && Objects.equals(choice.categoryId(), categoryId))
			.findFirst();
	}

	private static String describe(String description, BigDecimal amount) {
		return "\"" + description + "\" · ₹" + amount.abs().toPlainString() + (amount.signum() < 0 ? " out" : " in");
	}

	private static String reason(String text) {
		String reason = text.strip().replaceAll("\\s+", " ");
		if (reason.isEmpty()) {
			return "No reason given";
		}
		return reason.length() <= AiSuggestion.MAX_REASON_LENGTH ? reason
				: reason.substring(0, AiSuggestion.MAX_REASON_LENGTH - 1) + "…";
	}

	private static List<Integer> numbers(int count) {
		return IntStream.rangeClosed(1, count).boxed().toList();
	}

	private static Map<String, Object> orderedMap(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put(k1, v1);
		map.put(k2, v2);
		map.put(k3, v3);
		return map;
	}

}
