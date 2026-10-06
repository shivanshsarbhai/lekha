package com.lekha.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.lekha.TestcontainersConfiguration;

/**
 * Runs real SQL against Postgres. Each test starts from the same made-up September 2026, where the 1st is a Tuesday:
 * <ul>
 * <li>Tue 1st: salary +150000, INCOME</li>
 * <li>Sat 5th: dinner −2000, split into my share −500 (Test Food › Test Delivery) and −1500 LENT</li>
 * <li>Sun 6th: refund +199, EXPENSE (Test Food)</li>
 * <li>Mon 7th: SIP −10000, INVESTMENT</li>
 * <li>Tue 8th: −50000 to savings, TRANSFER</li>
 * <li>Wed 9th: −300, not classified</li>
 * </ul>
 * So spending is 500 − 199 = 301, all of it at the weekend.
 */
@JdbcTest
@Import({ TestcontainersConfiguration.class, AnalyticsRepository.class, AnalyticsService.class })
class AnalyticsServiceTest {

	private static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);

	private static final LocalDate SEP_30 = LocalDate.of(2026, 9, 30);

	@Autowired
	private AnalyticsService service;

	@Autowired
	private JdbcClient jdbc;

	private UUID hdfc;

	private UUID food;

	private UUID delivery;

	@BeforeEach
	void createSeptember() {
		hdfc = insertAccount("HDFC Salary");
		food = insertCategory("Test Food", null);
		delivery = insertCategory("Test Delivery", food);

		allocate(insertTransaction(hdfc, SEP_1, "150000.00", "NEFT"), "INCOME", null, "150000.00");
		UUID dinner = insertTransaction(hdfc, LocalDate.of(2026, 9, 5), "-2000.00", "UPI");
		allocate(dinner, "EXPENSE", delivery, "-500.00");
		allocate(dinner, "LENT", null, "-1500.00");
		allocate(insertTransaction(hdfc, LocalDate.of(2026, 9, 6), "199.00", "UPI"), "EXPENSE", food, "199.00");
		allocate(insertTransaction(hdfc, LocalDate.of(2026, 9, 7), "-10000.00", null), "INVESTMENT", null,
				"-10000.00");
		allocate(insertTransaction(hdfc, LocalDate.of(2026, 9, 8), "-50000.00", "IMPS"), "TRANSFER", null,
				"-50000.00");
		insertTransaction(hdfc, LocalDate.of(2026, 9, 9), "-300.00", "UPI");
	}

	@Test
	void everyMetricWithoutDimensionsIsOneTotalRow() {
		QueryResult result = query(List.of("spending", "income", "invested", "lent", "savings_rate",
				"transaction_count", "average_spend"), List.of(), Map.of(), SEP_1, SEP_30);

		assertThat(result.columns()).containsExactly("spending", "income", "invested", "lent", "savings_rate",
				"transaction_count", "average_spend");
		assertThat(result.rows()).containsExactly(row(money("301.00"), money("150000.00"), money("10000.00"),
				money("1500.00"), money("0.9980"), 5L, money("150.50")));
		assertThat(result.unclassifiedCount()).isEqualTo(1);
	}

	@Test
	void weekendVersusWeekday() {
		QueryResult result = query(List.of("spending", "income"), List.of("day_type"), Map.of(), SEP_1, SEP_30);

		assertThat(result.columns()).containsExactly("day_type", "spending", "income");
		assertThat(result.rows()).containsExactly(row("WEEKDAY", money("0.00"), money("150000.00")),
				row("WEEKEND", money("301.00"), money("0.00")));
	}

	@Test
	void filteringOnALabelDimensionWithoutGroupingByIt() {
		QueryResult result = query(List.of("spending"), List.of(), Map.of("day_type", List.of("WEEKEND")), SEP_1,
				SEP_30);

		assertThat(result.rows()).containsExactly(row(money("301.00")));
	}

	@Test
	void dayOfWeekRunsMondayToSunday() {
		QueryResult result = query(List.of("transaction_count"), List.of("day_of_week"), Map.of(), SEP_1, SEP_30);

		assertThat(result.rows()).containsExactly(row("MON", 1L), row("TUE", 2L), row("SAT", 1L), row("SUN", 1L));
	}

	@Test
	void monthsWithNothingAreFilledWithZero() {
		UUID july = insertTransaction(hdfc, LocalDate.of(2026, 7, 15), "-1000.00", "CARD");
		allocate(july, "EXPENSE", null, "-1000.00");

		QueryResult result = query(List.of("spending", "average_spend"), List.of("month"), Map.of(),
				LocalDate.of(2026, 7, 1), SEP_30);

		assertThat(result.rows()).containsExactly(row("2026-07", money("1000.00"), money("1000.00")),
				row("2026-08", money("0.00"), null), row("2026-09", money("301.00"), money("150.50")));
	}

	@Test
	void anEmptyRangeStillHasEveryPeriod() {
		QueryResult result = query(List.of("spending", "transaction_count"), List.of("month"), Map.of(),
				LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28));

		assertThat(result.rows()).containsExactly(row("2026-01", money("0.00"), 0L),
				row("2026-02", money("0.00"), 0L));
	}

	@Test
	void subCategoriesRollUpIntoTheirTopLevelCategoryAndUncategorisedComesLast() {
		UUID snack = insertTransaction(hdfc, LocalDate.of(2026, 9, 10), "-50.00", "UPI");
		allocate(snack, "EXPENSE", null, "-50.00");

		QueryResult result = query(List.of("spending"), List.of("category"), Map.of(), SEP_1, SEP_30);

		assertThat(result.columns()).containsExactly("category_id", "category", "spending");
		assertThat(result.rows()).containsExactly(row(food, "Test Food", money("301.00")),
				row(null, "Uncategorised", money("50.00")));
	}

	@Test
	void drillingIntoOneCategoryShowsItsSubCategories() {
		QueryResult result = query(List.of("spending"), List.of("subcategory"),
				Map.of("category", List.of(food.toString())), SEP_1, SEP_30);

		assertThat(result.rows()).containsExactly(row(food, "Test Food", money("-199.00")),
				row(delivery, "Test Food › Test Delivery", money("500.00")));
	}

	@Test
	void anAccountFilterLimitsTheResultAndTheUnclassifiedCount() {
		UUID card = insertAccount("Scapia Card");
		allocate(insertTransaction(card, LocalDate.of(2026, 9, 2), "-700.00", "CARD"), "EXPENSE", null, "-700.00");
		insertTransaction(card, LocalDate.of(2026, 9, 3), "-20.00", "CARD");

		QueryResult result = query(List.of("spending"), List.of("account"),
				Map.of("account", List.of(card.toString())), SEP_1, SEP_30);

		assertThat(result.rows()).containsExactly(row(card, "Scapia Card", money("700.00")));
		assertThat(result.unclassifiedCount()).isEqualTo(1);
	}

	@Test
	void kindsAreGroupedInTheirUsualOrder() {
		QueryResult result = query(List.of("transaction_count"), List.of("kind"), Map.of(), SEP_1, SEP_30);

		assertThat(result.rows()).containsExactly(row("EXPENSE", 2L), row("INCOME", 1L), row("INVESTMENT", 1L),
				row("LENT", 1L), row("TRANSFER", 1L));
	}

	@Test
	void paymentModeShowsUnknownForRowsWithoutOne() {
		QueryResult result = query(List.of("invested"), List.of("payment_mode"), Map.of(), SEP_1, SEP_30);

		assertThat(result.rows()).containsExactly(row("UNKNOWN", money("10000.00")));
	}

	@Test
	void twoDimensionsAreGapFilledForEachCombination() {
		QueryResult result = query(List.of("spending"), List.of("week", "day_type"), Map.of(),
				LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 13));

		assertThat(result.rows()).containsExactly(row("2026-08-31", "WEEKEND", money("301.00")),
				row("2026-09-07", "WEEKEND", money("0.00")));
	}

	@Test
	void rejectsQueriesItCannotAnswer() {
		assertRejected(List.of(), List.of(), Map.of(), "Ask for at least one metric");
		assertRejected(List.of("profit"), List.of(), Map.of(), "Unknown metric \"profit\"");
		assertRejected(List.of("spending"), List.of("weather"), Map.of(), "Unknown dimension \"weather\"");
		assertRejected(List.of("spending"), List.of("month", "week"), Map.of(),
				"Group by at most one of day, week, month, quarter and year");
		assertRejected(List.of("spending"), List.of("kind", "account", "category", "day_type"), Map.of(),
				"Group by at most 3 dimensions");
		assertRejected(List.of("spending"), List.of(), Map.of("month", List.of("2026-09")),
				"Filter time with \"from\" and \"to\" rather than \"month\"");
		assertRejected(List.of("spending"), List.of(), Map.of("account", List.of()),
				"Filter \"account\" needs at least one value");
	}

	@Test
	void rejectsRangesThatAreBackwardsOrTooLong() {
		assertThatThrownBy(() -> query(List.of("spending"), List.of(), Map.of(), SEP_30, SEP_1))
			.isInstanceOf(InvalidQueryException.class)
			.hasMessage("\"from\" (2026-09-30) must not be after \"to\" (2026-09-01)");
		assertThatThrownBy(() -> query(List.of("spending"), List.of(), Map.of(), LocalDate.of(2021, 9, 1), SEP_1))
			.isInstanceOf(InvalidQueryException.class)
			.hasMessage("The date range must be at most 5 years");
		assertThatThrownBy(() -> query(List.of("spending"), List.of("day"), Map.of(), LocalDate.of(2026, 6, 1),
				SEP_30))
			.isInstanceOf(InvalidQueryException.class)
			.hasMessage("Grouping by day allows at most 92 days. Try week or month.");
		assertThatThrownBy(() -> service.query(new AnalyticsQueryRequest(List.of("spending"), null, null, null, null)))
			.isInstanceOf(InvalidQueryException.class)
			.hasMessage("\"from\" and \"to\" are both required");
	}

	private QueryResult query(List<String> metrics, List<String> dimensions, Map<String, List<String>> filters,
			LocalDate from, LocalDate to) {
		return service.query(new AnalyticsQueryRequest(metrics, dimensions, filters, from, to));
	}

	private void assertRejected(List<String> metrics, List<String> dimensions, Map<String, List<String>> filters,
			String messageStart) {
		assertThatThrownBy(() -> query(metrics, dimensions, filters, SEP_1, SEP_30))
			.isInstanceOf(InvalidQueryException.class)
			.hasMessageStartingWith(messageStart);
	}

	private static List<@Nullable Object> row(@Nullable Object... values) {
		return Arrays.asList(values);
	}

	private static BigDecimal money(String value) {
		return new BigDecimal(value);
	}

	private UUID insertAccount(String nickname) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO accounts (id, nickname, type, institution, last4, created_at)
				VALUES (:id, :nickname, 'BANK', 'HDFC', NULL, now())
				""").param("id", id).param("nickname", nickname).update();
		return id;
	}

	private UUID insertCategory(String name, @Nullable UUID parentId) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO categories (id, parent_id, name, kind, created_at)
				VALUES (:id, :parentId, :name, 'EXPENSE', now())
				""").param("id", id).param("parentId", parentId).param("name", name).update();
		return id;
	}

	private UUID insertTransaction(UUID accountId, LocalDate date, String amount, @Nullable String paymentMode) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO transactions (id, account_id, transaction_date, description, amount, payment_mode,
				                          metadata, created_at)
				VALUES (:id, :accountId, :date, 'TEST', :amount, :paymentMode, '{}'::jsonb, now())
				""")
			.param("id", id)
			.param("accountId", accountId)
			.param("date", date)
			.param("amount", new BigDecimal(amount))
			.param("paymentMode", paymentMode)
			.update();
		return id;
	}

	private void allocate(UUID transactionId, String kind, @Nullable UUID categoryId, String amount) {
		jdbc.sql("""
				INSERT INTO allocations (id, transaction_id, kind, category_id, amount, note)
				VALUES (:id, :transactionId, :kind, :categoryId, :amount, NULL)
				""")
			.param("id", UUID.randomUUID())
			.param("transactionId", transactionId)
			.param("kind", kind)
			.param("categoryId", categoryId)
			.param("amount", new BigDecimal(amount))
			.update();
	}

}
