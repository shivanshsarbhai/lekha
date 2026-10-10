package com.lekha.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.lekha.accounts.AccountType;
import com.lekha.categories.Category;
import com.lekha.categories.CategoryKind;

class ClassificationPromptTest {

	private static final Category FOOD = category(null, "Food", CategoryKind.EXPENSE);

	private static final Category ORDERING_IN = category(FOOD.id(), "Ordering in", CategoryKind.EXPENSE);

	private static final Category GROCERIES = category(FOOD.id(), "Groceries", CategoryKind.EXPENSE);

	private static final Category BILLS = category(null, "Bills", CategoryKind.EXPENSE);

	private static final Category SALARY = category(null, "Salary", CategoryKind.INCOME);

	private static final Category CARD_BILL = category(null, "Credit card bill payment", CategoryKind.TRANSFER);

	private static final List<ClassificationPrompt.Choice> CHOICES = ClassificationPrompt
		.choicesFrom(List.of(SALARY, ORDERING_IN, FOOD, CARD_BILL, GROCERIES, BILLS));

	private static final ClassificationPrompt.Item SWIGGY = new ClassificationPrompt.Item(UUID.randomUUID(),
			"UPI-SWIGGY-TEST", new BigDecimal("-450.00"), AccountType.BANK,
			List.of(new ClassificationPrompt.Example("UPI-SWIGGY-OLD", new BigDecimal("-380.00"), AllocationKind.EXPENSE,
					ORDERING_IN.id())));

	private static final ClassificationPrompt.Item ACME = new ClassificationPrompt.Item(UUID.randomUUID(),
			"NEFT CR-ACME CORP", new BigDecimal("150000.00"), AccountType.BANK, List.of());

	@Test
	void numbersEveryCategoryByKindWithSubCategoriesUnderTheirParentAndMoneyLentLast() {
		assertThat(CHOICES).extracting(ClassificationPrompt.Choice::number, ClassificationPrompt.Choice::label)
			.containsExactly(tuple(1, "Expense: Bills"),
					tuple(2, "Expense: Food"),
					tuple(3, "Expense: Food › Groceries"),
					tuple(4, "Expense: Food › Ordering in"),
					tuple(5, "Income: Salary"),
					tuple(6, "Transfer: Credit card bill payment"),
					tuple(7, "Lent: paid for someone else"));
		assertThat(CHOICES.get(3).categoryId()).isEqualTo(ORDERING_IN.id());
		assertThat(CHOICES.get(6).kind()).isEqualTo(AllocationKind.LENT);
		assertThat(CHOICES.get(6).categoryId()).isNull();
	}

	@Test
	void theSystemMessageListsTheOptions() {
		String system = prompt(SWIGGY).system();

		assertThat(system).contains("4 = Expense: Food › Ordering in").contains("7 = Lent: paid for someone else");
	}

	@Test
	void theUserMessageShowsEachTransactionWithItsExamplesAsOptions() {
		String user = prompt(SWIGGY, ACME).user();

		assertThat(user).contains("Transaction 1: \"UPI-SWIGGY-TEST\" · ₹450.00 out · bank account")
			.contains("  - \"UPI-SWIGGY-OLD\" · ₹380.00 out → option 4 (Expense: Food › Ordering in)")
			.contains("Transaction 2: \"NEFT CR-ACME CORP\" · ₹150000.00 in · bank account")
			.contains("They haven't classified anything similar.")
			.endsWith("Answer for every transaction, 1 to 2.");
	}

	@Test
	@SuppressWarnings("unchecked")
	void theSchemaOnlyAllowsTransactionsAndOptionsThatExist() {
		Map<String, Object> answers = (Map<String, Object>) ((Map<String, Object>) prompt(SWIGGY, ACME).schema()
			.get("properties")).get("answers");
		Map<String, Object> answer = (Map<String, Object>) answers.get("items");
		Map<String, Map<String, Object>> fields = (Map<String, Map<String, Object>>) answer.get("properties");

		assertThat(answers).containsEntry("minItems", 2).containsEntry("maxItems", 2);
		assertThat(fields.keySet()).containsExactly("transaction", "reason", "option");
		assertThat(fields.get("transaction").get("enum")).isEqualTo(List.of(1, 2));
		assertThat(fields.get("option").get("enum")).isEqualTo(List.of(1, 2, 3, 4, 5, 6, 7));
	}

	@Test
	void turnsOptionNumbersIntoKindsAndCategories() {
		Map<UUID, AiSuggestion> suggestions = prompt(SWIGGY, ACME).parse("""
				{"answers": [
				  {"transaction": 1, "reason": "Swiggy is food delivery", "option": 4},
				  {"transaction": 2, "reason": "  Salary   from ACME ", "option": 5}
				]}
				""");

		assertThat(suggestions).containsExactly(
				Map.entry(SWIGGY.transactionId(), new AiSuggestion(SWIGGY.transactionId(), AllocationKind.EXPENSE,
						ORDERING_IN.id(), "Swiggy is food delivery", "test-model")),
				Map.entry(ACME.transactionId(), new AiSuggestion(ACME.transactionId(), AllocationKind.INCOME, SALARY.id(),
						"Salary from ACME", "test-model")));
	}

	@Test
	void dropsAnswersForTransactionsOrOptionsThatDontExistAndRepeatedAnswers() {
		Map<UUID, AiSuggestion> suggestions = prompt(SWIGGY, ACME).parse("""
				{"answers": [
				  {"transaction": 3, "reason": "no such transaction", "option": 1},
				  {"transaction": 2, "reason": "no such option", "option": 8},
				  {"transaction": 1, "reason": "first", "option": 2},
				  {"transaction": 1, "reason": "second", "option": 3}
				]}
				""");

		assertThat(suggestions).containsOnlyKeys(SWIGGY.transactionId());
		assertThat(suggestions.get(SWIGGY.transactionId()).reason()).isEqualTo("first");
	}

	@Test
	void aReplyThatIsntJsonGivesNoSuggestions() {
		assertThat(prompt(SWIGGY).parse("Sure! Here is my answer: Food")).isEmpty();
	}

	@Test
	void fillsInAMissingReasonAndShortensALongOne() {
		Map<UUID, AiSuggestion> suggestions = prompt(SWIGGY, ACME).parse("""
				{"answers": [
				  {"transaction": 1, "option": 4},
				  {"transaction": 2, "reason": "%s", "option": 5}
				]}
				""".formatted("x".repeat(400)));

		assertThat(suggestions.get(SWIGGY.transactionId()).reason()).isEqualTo("No reason given");
		assertThat(suggestions.get(ACME.transactionId()).reason()).hasSize(AiSuggestion.MAX_REASON_LENGTH).endsWith("…");
	}

	@Test
	void labelsAKindAndCategoryAsTheOptionsDo() {
		assertThat(ClassificationPrompt.labelOf(CHOICES, AllocationKind.EXPENSE, GROCERIES.id()))
			.isEqualTo("Expense: Food › Groceries");
		assertThat(ClassificationPrompt.labelOf(CHOICES, AllocationKind.INVESTMENT, null)).isEqualTo("Investment");
	}

	@Test
	void needsAtLeastOneTransaction() {
		assertThatThrownBy(() -> new ClassificationPrompt(CHOICES, List.of(), "test-model"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private static ClassificationPrompt prompt(ClassificationPrompt.Item... items) {
		return new ClassificationPrompt(CHOICES, List.of(items), "test-model");
	}

	private static Category category(UUID parentId, String name, CategoryKind kind) {
		return new Category(UUID.randomUUID(), parentId, name, kind, Instant.parse("2026-01-01T00:00:00Z"));
	}

}
