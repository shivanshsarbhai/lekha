package com.lekha.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(AiController.class)
class AiControllerTest {

	private static final AiClassifier.Status WORKING = new AiClassifier.Status("qwen2.5:7b", true, null,
			AiClassifier.Job.EVALUATING, 12, 3,
			new AiClassifier.Evaluation("qwen2.5:7b", 50, 10, 8, false, List.of(new AiClassifier.Miss(
					"UPI-BIGBASKET-TEST", "Expense: Food › Groceries", "Expense: Food › Ordering in", "Sounds like food"))));

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	AiClassifier classifier;

	@BeforeEach
	void setUp() {
		given(classifier.status()).willReturn(WORKING);
	}

	@Test
	void statusSaysWhatTheAiIsDoing() {
		var response = assertThat(mvc.get().uri("/api/v1/ai/status")).hasStatusOk().bodyJson();

		response.extractingPath("$.model").isEqualTo("qwen2.5:7b");
		response.extractingPath("$.available").isEqualTo(true);
		response.extractingPath("$.working").isEqualTo("EVALUATING");
		response.extractingPath("$.waiting").isEqualTo(12);
		response.extractingPath("$.suggestedThisRun").isEqualTo(3);
		response.extractingPath("$.evaluation.correct").isEqualTo(8);
		response.extractingPath("$.evaluation.misses[0].yourChoice").isEqualTo("Expense: Food › Groceries");
	}

	@Test
	void startsSuggesting() {
		assertThat(mvc.post().uri("/api/v1/ai/suggestions")).hasStatusOk();

		then(classifier).should().suggestInBackground();
	}

	@Test
	void startsMeasuring() {
		assertThat(mvc.post().uri("/api/v1/ai/evaluation")).hasStatusOk();

		then(classifier).should().evaluateInBackground();
	}

	@Test
	void stops() {
		assertThat(mvc.post().uri("/api/v1/ai/stop")).hasStatusOk();

		then(classifier).should().stop();
	}

}
