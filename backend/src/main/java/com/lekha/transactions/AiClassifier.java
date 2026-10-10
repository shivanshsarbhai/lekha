package com.lekha.transactions;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.lekha.categories.CategoryService;

import jakarta.annotation.PreDestroy;

/**
 * Asks the language model what unclassified transactions are, a few at a time, in the background. Its answers are
 * stored as suggestions and never saved as classifications. One job runs at a time: suggesting, or measuring how often
 * the model agrees with choices you've already made.
 */
@Service
public class AiClassifier {

	private static final Logger log = LoggerFactory.getLogger(AiClassifier.class);

	/** Transactions per request. Small batches keep each answer quick and stop a slow one blocking many. */
	static final int BATCH_SIZE = 5;

	static final int EXAMPLES_PER_TRANSACTION = 8;

	static final int EVALUATION_SIZE = 50;

	private static final int MAX_MISSES = 10;

	enum Job {
		SUGGESTING, EVALUATING
	}

	/** A transaction the model got wrong while being measured. */
	record Miss(String description, String yourChoice, String aiChoice, String reason) {
	}

	/** How a measurement went, or is going: {@code correct} of the {@code checked} so far matched your choice. */
	record Evaluation(String model, int total, int checked, int correct, boolean finished, List<Miss> misses) {
	}

	/**
	 * {@code waiting} counts unclassified transactions with no suggestion yet. {@code problem} says why the model
	 * can't be used, or why the last job stopped early.
	 */
	record Status(String model, boolean available, @Nullable String problem, @Nullable Job working, int waiting,
			int suggestedThisRun, @Nullable Evaluation evaluation) {
	}

	private final AiSuggestionRepository repository;

	private final CategoryService categories;

	private final LanguageModel model;

	private final Clock clock;

	private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("ai").factory());

	private volatile @Nullable Job working;

	private volatile boolean stopRequested;

	private volatile @Nullable String lastProblem;

	private volatile int suggestedThisRun;

	private volatile @Nullable Evaluation evaluation;

	AiClassifier(AiSuggestionRepository repository, CategoryService categories, LanguageModel model, Clock clock) {
		this.repository = repository;
		this.categories = categories;
		this.model = model;
		this.clock = clock;
	}

	/** Starts suggesting for every unclassified transaction, unless a job is already running. Returns at once. */
	public void suggestInBackground() {
		start(Job.SUGGESTING, this::suggestAll);
	}

	void evaluateInBackground() {
		start(Job.EVALUATING, this::evaluate);
	}

	/** The running job stops after the batch the model is working on. */
	void stop() {
		stopRequested = true;
	}

	Status status() {
		LanguageModel.Status modelStatus = model.status();
		String problem = modelStatus.available() ? lastProblem : modelStatus.problem();
		return new Status(model.name(), modelStatus.available(), problem, working, repository.countWaiting(),
				suggestedThisRun, evaluation);
	}

	/** Runs on the caller's thread, so tests can wait for it. */
	void suggestAll() {
		List<ClassificationPrompt.Choice> choices = ClassificationPrompt.choicesFrom(categories.allCategories());
		// The model may skip a transaction; remembering what it was asked stops the same batch being asked forever.
		Set<UUID> asked = new HashSet<>();
		suggestedThisRun = 0;
		while (!stopRequested) {
			List<AiSuggestionRepository.Pending> batch = repository.waiting(asked.size() + BATCH_SIZE)
				.stream()
				.filter(pending -> !asked.contains(pending.id()))
				.limit(BATCH_SIZE)
				.toList();
			if (batch.isEmpty()) {
				return;
			}
			batch.forEach(pending -> asked.add(pending.id()));
			Map<UUID, AiSuggestion> answers = ask(choices, batch, false);
			answers.values().forEach(suggestion -> repository.saveIfStillUseful(suggestion, clock.instant()));
			suggestedThisRun += answers.size();
		}
	}

	/**
	 * Hides your choice for each of your most recently classified transactions, asks the model, and compares. Each
	 * transaction is shown only examples dated before it, as if it had just been imported.
	 */
	void evaluate() {
		List<ClassificationPrompt.Choice> choices = ClassificationPrompt.choicesFrom(categories.allCategories());
		List<AiSuggestionRepository.Labelled> sample = repository.recentlyClassified(EVALUATION_SIZE);
		List<Miss> misses = new ArrayList<>();
		int checked = 0;
		int correct = 0;
		evaluation = new Evaluation(model.name(), sample.size(), 0, 0, sample.isEmpty(), List.of());
		for (int start = 0; start < sample.size() && !stopRequested; start += BATCH_SIZE) {
			List<AiSuggestionRepository.Labelled> batch = sample.subList(start, Math.min(start + BATCH_SIZE, sample.size()));
			Map<UUID, AiSuggestion> answers = ask(choices,
					batch.stream().map(AiSuggestionRepository.Labelled::transaction).toList(), true);
			for (AiSuggestionRepository.Labelled labelled : batch) {
				checked++;
				AiSuggestion answer = answers.get(labelled.transaction().id());
				if (answer != null && answer.sameChoiceAs(labelled.kind(), labelled.categoryId())) {
					correct++;
				}
				else if (misses.size() < MAX_MISSES) {
					misses.add(new Miss(labelled.transaction().description(),
							ClassificationPrompt.labelOf(choices, labelled.kind(), labelled.categoryId()),
							answer == null ? "No answer" : ClassificationPrompt.labelOf(choices, answer.kind(), answer.categoryId()),
							answer == null ? "" : answer.reason()));
				}
			}
			evaluation = new Evaluation(model.name(), sample.size(), checked, correct, false, List.copyOf(misses));
		}
		evaluation = new Evaluation(model.name(), sample.size(), checked, correct, true, List.copyOf(misses));
	}

	/** {@code onlyEarlierExamples} hides examples dated on or after each transaction. */
	private Map<UUID, AiSuggestion> ask(List<ClassificationPrompt.Choice> choices,
			List<AiSuggestionRepository.Pending> batch, boolean onlyEarlierExamples) {
		List<ClassificationPrompt.Item> items = batch.stream().map(pending -> {
			LocalDate before = onlyEarlierExamples ? pending.transactionDate() : null;
			return new ClassificationPrompt.Item(pending.id(), pending.description(), pending.amount(),
					pending.accountType(), repository.similarExamples(pending, before, EXAMPLES_PER_TRANSACTION));
		}).toList();
		ClassificationPrompt prompt = new ClassificationPrompt(choices, items, model.name());
		return prompt.parse(model.chat(prompt.system(), prompt.user(), prompt.schema()));
	}

	private synchronized void start(Job job, Runnable body) {
		if (working != null) {
			return;
		}
		working = job;
		stopRequested = false;
		lastProblem = null;
		worker.submit(() -> {
			try {
				body.run();
			}
			catch (ModelUnavailableException ex) {
				lastProblem = ex.getMessage();
			}
			catch (RuntimeException ex) {
				log.error("AI job {} failed", job, ex);
				lastProblem = "Something went wrong: " + ex.getMessage();
			}
			finally {
				working = null;
			}
		});
	}

	@PreDestroy
	void shutDown() {
		worker.shutdownNow();
	}

}
