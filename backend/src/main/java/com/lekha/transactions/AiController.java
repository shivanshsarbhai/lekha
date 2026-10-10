package com.lekha.transactions;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Starts and watches the AI's background jobs. Each POST returns at once; poll the status to follow progress. */
@RestController
@RequestMapping("/api/v1/ai")
class AiController {

	private final AiClassifier classifier;

	AiController(AiClassifier classifier) {
		this.classifier = classifier;
	}

	@GetMapping("/status")
	AiClassifier.Status status() {
		return classifier.status();
	}

	/** Suggests for every unclassified transaction that doesn't have a suggestion yet. */
	@PostMapping("/suggestions")
	AiClassifier.Status suggest() {
		classifier.suggestInBackground();
		return classifier.status();
	}

	@PostMapping("/evaluation")
	AiClassifier.Status evaluate() {
		classifier.evaluateInBackground();
		return classifier.status();
	}

	@PostMapping("/stop")
	AiClassifier.Status stop() {
		classifier.stop();
		return classifier.status();
	}

}
