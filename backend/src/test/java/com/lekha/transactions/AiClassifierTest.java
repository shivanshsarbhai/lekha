package com.lekha.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lekha.TestcontainersConfiguration;
import com.lekha.categories.Category;
import com.lekha.categories.CategoryKind;
import com.lekha.categories.CategoryService;

/**
 * Runs the AI jobs against a real database with a fake model, which answers each transaction with whatever option
 * {@link #answer} picks for its description. Rows persist between tests, so they are deleted after each one.
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ TestcontainersConfiguration.class, TransactionRepository.class, AllocationRepository.class,
		AiSuggestionRepository.class, AiClassifier.class, AiClassifierTest.FixedClock.class })
class AiClassifierTest {

	private static final Pattern TRANSACTION_LINE = Pattern.compile("^Transaction (\\d+): \"([^\"]+)\"", Pattern.MULTILINE);

	@TestConfiguration
	static class FixedClock {

		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC);
		}

	}

	@Autowired
	private AiClassifier classifier;

	@Autowired
	private TransactionRepository transactions;

	@Autowired
	private AllocationRepository allocations;

	@Autowired
	private JdbcClient jdbc;

	@MockitoBean
	private CategoryService categories;

	@MockitoBean
	private LanguageModel model;

	private final List<String> prompts = new ArrayList<>();

	/** The option the fake model picks for a description; null leaves it unanswered. */
	private Function<String, Integer> answer = description -> null;

	private List<ClassificationPrompt.Choice> choices;

	private UUID account;

	@BeforeEach
	void setUp() {
		List<Category> seeded = jdbc.sql("SELECT id, parent_id, name, kind, created_at FROM categories")
			.query((rs, rowNum) -> new Category(rs.getObject("id", UUID.class), rs.getObject("parent_id", UUID.class),
					rs.getString("name"), CategoryKind.valueOf(rs.getString("kind")),
					rs.getTimestamp("created_at").toInstant()))
			.list();
		given(categories.allCategories()).willReturn(seeded);
		choices = ClassificationPrompt.choicesFrom(seeded);

		given(model.name()).willReturn("test-model");
		given(model.status()).willReturn(new LanguageModel.Status(true, null));
		given(model.chat(anyString(), anyString(), anyMap())).willAnswer(call -> {
			String user = call.getArgument(1);
			prompts.add(user);
			Matcher lines = TRANSACTION_LINE.matcher(user);
			List<String> answers = new ArrayList<>();
			while (lines.find()) {
				Integer option = answer.apply(lines.group(2));
				if (option != null) {
					answers.add("{\"transaction\": %s, \"reason\": \"test reason\", \"option\": %d}".formatted(lines.group(1),
							option));
				}
			}
			return "{\"answers\": [" + String.join(",", answers) + "]}";
		});

		account = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO accounts (id, nickname, type, institution, last4, created_at)
				VALUES (:id, 'Test bank', 'BANK', 'HDFC', NULL, now())
				""").param("id", account).update();
	}

	@AfterEach
	void deleteEverything() {
		jdbc.sql("DELETE FROM transactions").update();
		jdbc.sql("DELETE FROM accounts").update();
	}

	@Test
	void suggestsForUnclassifiedTransactionsWithoutClassifyingThem() {
		Transaction swiggy = insert("UPI-SWIGGY-TEST", "-450.00", LocalDate.of(2026, 9, 3));
		Transaction zomato = insert("UPI-ZOMATO-TEST", "-300.00", LocalDate.of(2026, 9, 4));
		Transaction classified = insert("UPI-ALREADY-DONE", "-100.00", LocalDate.of(2026, 9, 5));
		classify(classified, "Food", "Groceries");
		answer = description -> option("Food", "Ordering in");

		classifier.suggestAll();

		assertThat(suggestionsByDescription()).containsOnly(Map.entry("UPI-SWIGGY-TEST", "Ordering in"),
				Map.entry("UPI-ZOMATO-TEST", "Ordering in"));
		assertThat(allocations.findByTransactionIds(List.of(swiggy.id(), zomato.id()))).isEmpty();
		AiClassifier.Status status = classifier.status();
		assertThat(status.waiting()).isZero();
		assertThat(status.suggestedThisRun()).isEqualTo(2);
	}

	@Test
	void showsTheModelYourPastChoicesForSimilarTransactionsOnly() {
		classify(insert("UPI-SWIGGY-111111-Order", "-380.00", LocalDate.of(2026, 8, 1)), "Food", "Ordering in");
		classify(insert("AMAZON PAY INDIA PVT LTD", "-999.00", LocalDate.of(2026, 8, 2)), "Shopping", null);
		insert("UPI-SWIGGY-222222-Order", "-450.00", LocalDate.of(2026, 9, 3));

		classifier.suggestAll();

		assertThat(prompts).hasSize(1);
		assertThat(prompts.getFirst())
			.contains("\"UPI-SWIGGY-111111-Order\" · ₹380.00 out → option " + option("Food", "Ordering in"))
			.doesNotContain("AMAZON");
	}

	@Test
	void asksInBatchesAndStopsWhenTheModelSkipsTransactions() {
		for (int day = 1; day <= 7; day++) {
			insert("UPI-SHOP-" + day, "-10" + day + ".00", LocalDate.of(2026, 9, day));
		}

		classifier.suggestAll();

		then(model).should(times(2)).chat(anyString(), anyString(), anyMap());
		assertThat(prompts.get(0)).contains("Answer for every transaction, 1 to 5.");
		assertThat(prompts.get(1)).contains("Answer for every transaction, 1 to 2.");
		assertThat(classifier.status().waiting()).isEqualTo(7);
	}

	@Test
	void classifyingATransactionRemovesItsSuggestion() {
		Transaction swiggy = insert("UPI-SWIGGY-TEST", "-450.00", LocalDate.of(2026, 9, 3));
		answer = description -> option("Food", "Ordering in");
		classifier.suggestAll();

		classify(swiggy, "Food", "Dining out");

		assertThat(suggestionsByDescription()).isEmpty();
	}

	@Test
	void doesntSaveASuggestionForATransactionClassifiedWhileTheModelWasThinking() {
		Transaction swiggy = insert("UPI-SWIGGY-TEST", "-450.00", LocalDate.of(2026, 9, 3));
		answer = description -> {
			classify(swiggy, "Food", "Dining out");
			return option("Food", "Ordering in");
		};

		classifier.suggestAll();

		assertThat(suggestionsByDescription()).isEmpty();
	}

	@Test
	void measuresTheModelAgainstYourChoicesShowingOnlyEarlierExamples() {
		classify(insert("UPI-SWIGGY-111111-Order", "-380.00", LocalDate.of(2026, 9, 1)), "Food", "Ordering in");
		classify(insert("UPI-BIGBASKET-TEST", "-1200.00", LocalDate.of(2026, 9, 5)), "Food", "Groceries");
		classify(insert("UPI-SWIGGY-222222-Order", "-450.00", LocalDate.of(2026, 9, 10)), "Food", "Ordering in");
		answer = description -> option("Food", "Ordering in");

		classifier.evaluate();

		AiClassifier.Evaluation evaluation = classifier.status().evaluation();
		assertThat(evaluation.total()).isEqualTo(3);
		assertThat(evaluation.checked()).isEqualTo(3);
		assertThat(evaluation.correct()).isEqualTo(2);
		assertThat(evaluation.finished()).isTrue();
		assertThat(evaluation.misses()).containsExactly(new AiClassifier.Miss("UPI-BIGBASKET-TEST",
				"Expense: Food › Groceries", "Expense: Food › Ordering in", "test reason"));

		// Newest first: the 10 September Swiggy payment sees the 1 September one, which sees nothing before it.
		String prompt = prompts.getFirst();
		assertThat(prompt.substring(0, prompt.indexOf("Transaction 2:"))).contains("UPI-SWIGGY-111111-Order");
		assertThat(prompt.substring(prompt.indexOf("Transaction 3:"))).contains("They haven't classified anything similar.");
	}

	@Test
	void reportsWhyABackgroundRunStopped() {
		insert("UPI-SWIGGY-TEST", "-450.00", LocalDate.of(2026, 9, 3));
		given(model.chat(anyString(), anyString(), anyMap()))
			.willThrow(new ModelUnavailableException("Ollama isn't running"));

		classifier.suggestInBackground();

		await().atMost(Duration.ofSeconds(5)).until(() -> classifier.status().working() == null);
		assertThat(classifier.status().problem()).isEqualTo("Ollama isn't running");
	}

	private Transaction insert(String description, String amount, LocalDate date) {
		Transaction transaction = Transaction.create(account, date, null, description, new BigDecimal(amount), null,
				PaymentMode.UPI, Map.of());
		transactions.insert(transaction);
		return transaction;
	}

	private void classify(Transaction transaction, String parent, String child) {
		UUID categoryId = category(parent, child).id();
		allocations.replace(transaction.id(), List.of(Allocation.create(transaction.id(), AllocationKind.EXPENSE,
				categoryId, transaction.amount(), null)));
	}

	private int option(String parent, String child) {
		UUID categoryId = category(parent, child).id();
		return choices.stream().filter(choice -> categoryId.equals(choice.categoryId())).findFirst().orElseThrow().number();
	}

	private Category category(String parent, String child) {
		List<Category> all = categories.allCategories();
		Category top = all.stream()
			.filter(category -> category.parentId() == null && category.name().equals(parent))
			.findFirst()
			.orElseThrow();
		if (child == null) {
			return top;
		}
		return all.stream()
			.filter(category -> top.id().equals(category.parentId()) && category.name().equals(child))
			.findFirst()
			.orElseThrow();
	}

	/** Each suggested transaction's description, with the name of the category suggested for it. */
	private Map<String, String> suggestionsByDescription() {
		return jdbc.sql("""
				SELECT t.description, c.name
				FROM ai_suggestions s
				JOIN transactions t ON t.id = s.transaction_id
				JOIN categories c ON c.id = s.category_id
				""")
			.query((rs, rowNum) -> Map.entry(rs.getString("description"), rs.getString("name")))
			.list()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

}
