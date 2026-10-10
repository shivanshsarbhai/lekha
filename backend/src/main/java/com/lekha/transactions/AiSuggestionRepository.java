package com.lekha.transactions;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.lekha.accounts.AccountType;

@Repository
class AiSuggestionRepository {

	/**
	 * How alike two descriptions must be, from 0 to 1, for one to be shown as an example for the other. Digits are
	 * removed first, since reference numbers make every narration look different.
	 */
	static final double MIN_SIMILARITY = 0.25;

	/**
	 * Only transactions classified as one piece of a category (or as money lent) teach the model anything: a split or
	 * a linked refund says nothing simple about the merchant.
	 */
	private static final String CLEAR_EXAMPLE = """
			(SELECT count(*) FROM allocations x WHERE x.transaction_id = t.id) = 1
			AND a.refund_of IS NULL
			AND (a.category_id IS NOT NULL OR a.kind = 'LENT')
			""";

	/** An unclassified transaction, with what the model needs to know about it. */
	record Pending(UUID id, LocalDate transactionDate, String description, BigDecimal amount, AccountType accountType) {
	}

	/** A transaction the user classified as one piece, used to measure the model. */
	record Labelled(Pending transaction, AllocationKind kind, @Nullable UUID categoryId) {
	}

	private final JdbcClient jdbc;

	AiSuggestionRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Map<UUID, AiSuggestion> findByTransactionIds(Collection<UUID> transactionIds) {
		if (transactionIds.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
				SELECT transaction_id, kind, category_id, reason, model
				FROM ai_suggestions
				WHERE transaction_id IN (:transactionIds)
				""")
				.param("transactionIds", List.copyOf(transactionIds))
				.query((rs, rowNum) -> new AiSuggestion(rs.getObject("transaction_id", UUID.class),
						AllocationKind.valueOf(rs.getString("kind")), rs.getObject("category_id", UUID.class),
						rs.getString("reason"), rs.getString("model")))
				.list()
				.stream()
				.collect(Collectors.toMap(AiSuggestion::transactionId, suggestion -> suggestion));
	}

	/**
	 * Saves the suggestion, replacing an older one, unless the transaction was classified or its category deleted
	 * while the model was thinking.
	 */
	void saveIfStillUseful(AiSuggestion suggestion, Instant suggestedAt) {
		jdbc.sql("""
				INSERT INTO ai_suggestions (transaction_id, kind, category_id, reason, model, suggested_at)
				SELECT :transactionId, :kind, :categoryId, :reason, :model, :suggestedAt
				WHERE NOT EXISTS (SELECT 1 FROM allocations WHERE transaction_id = :transactionId)
				  AND EXISTS (SELECT 1 FROM transactions WHERE id = :transactionId)
				  AND (CAST(:categoryId AS uuid) IS NULL OR EXISTS (SELECT 1 FROM categories WHERE id = :categoryId))
				ON CONFLICT (transaction_id) DO UPDATE
				SET kind = EXCLUDED.kind, category_id = EXCLUDED.category_id, reason = EXCLUDED.reason,
				    model = EXCLUDED.model, suggested_at = EXCLUDED.suggested_at
				""")
				.param("transactionId", suggestion.transactionId())
				.param("kind", suggestion.kind().name())
				.param("categoryId", suggestion.categoryId())
				.param("reason", suggestion.reason())
				.param("model", suggestion.model())
				.param("suggestedAt", OffsetDateTime.ofInstant(suggestedAt, ZoneOffset.UTC))
				.update();
	}

	void deleteFor(UUID transactionId) {
		jdbc.sql("DELETE FROM ai_suggestions WHERE transaction_id = :transactionId")
				.param("transactionId", transactionId)
				.update();
	}

	/** Unclassified transactions the model hasn't looked at, newest first so the months you're working on fill in first. */
	List<Pending> waiting(int limit) {
		return jdbc.sql("""
				SELECT t.id, t.transaction_date, t.description, t.amount, acc.type
				FROM transactions t
				JOIN accounts acc ON acc.id = t.account_id
				WHERE NOT EXISTS (SELECT 1 FROM allocations a WHERE a.transaction_id = t.id)
				  AND NOT EXISTS (SELECT 1 FROM ai_suggestions s WHERE s.transaction_id = t.id)
				ORDER BY t.transaction_date DESC, t.id
				LIMIT :limit
				""")
				.param("limit", limit)
				.query((rs, rowNum) -> mapPending(rs))
				.list();
	}

	int countWaiting() {
		return jdbc.sql("""
				SELECT count(*)
				FROM transactions t
				WHERE NOT EXISTS (SELECT 1 FROM allocations a WHERE a.transaction_id = t.id)
				  AND NOT EXISTS (SELECT 1 FROM ai_suggestions s WHERE s.transaction_id = t.id)
				""")
				.query(Integer.class)
				.single();
	}

	/**
	 * The user's clearest past choices for transactions whose descriptions look most like this one, going the same
	 * way (money out or money in). {@code before} limits them to earlier transactions, for measuring the model fairly.
	 */
	List<ClassificationPrompt.Example> similarExamples(Pending transaction, @Nullable LocalDate before, int limit) {
		return jdbc.sql("""
				SELECT t.description, t.amount, a.kind, a.category_id
				FROM transactions t
				JOIN allocations a ON a.transaction_id = t.id
				WHERE t.id <> :id
				  AND (t.amount < 0) = :outgoing
				  AND (CAST(:before AS date) IS NULL OR t.transaction_date < :before)
				  AND %s
				  AND similarity(regexp_replace(lower(t.description), '[0-9]', '', 'g'), :text) >= :minSimilarity
				ORDER BY similarity(regexp_replace(lower(t.description), '[0-9]', '', 'g'), :text) DESC,
				         t.transaction_date DESC, t.id
				LIMIT :limit
				""".formatted(CLEAR_EXAMPLE))
				.param("id", transaction.id())
				.param("outgoing", transaction.amount().signum() < 0)
				.param("before", before)
				.param("text", withoutDigits(transaction.description()))
				.param("minSimilarity", MIN_SIMILARITY)
				.param("limit", limit)
				.query((rs, rowNum) -> new ClassificationPrompt.Example(rs.getString("description"),
						rs.getBigDecimal("amount"), AllocationKind.valueOf(rs.getString("kind")),
						rs.getObject("category_id", UUID.class)))
				.list();
	}

	/** The most recently dated transactions classified clearly enough to check the model against. */
	List<Labelled> recentlyClassified(int limit) {
		return jdbc.sql("""
				SELECT t.id, t.transaction_date, t.description, t.amount, acc.type, a.kind, a.category_id
				FROM transactions t
				JOIN accounts acc ON acc.id = t.account_id
				JOIN allocations a ON a.transaction_id = t.id
				WHERE %s
				ORDER BY t.transaction_date DESC, t.id
				LIMIT :limit
				""".formatted(CLEAR_EXAMPLE))
				.param("limit", limit)
				.query((rs, rowNum) -> new Labelled(mapPending(rs), AllocationKind.valueOf(rs.getString("kind")),
						rs.getObject("category_id", UUID.class)))
				.list();
	}

	static String withoutDigits(String description) {
		return description.toLowerCase(Locale.ROOT).replaceAll("[0-9]", "");
	}

	private static Pending mapPending(ResultSet rs) throws SQLException {
		return new Pending(rs.getObject("id", UUID.class), rs.getObject("transaction_date", LocalDate.class),
				rs.getString("description"), rs.getBigDecimal("amount"), AccountType.valueOf(rs.getString("type")));
	}

}
