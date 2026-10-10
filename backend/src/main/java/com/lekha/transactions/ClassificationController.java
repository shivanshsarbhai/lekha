package com.lekha.transactions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
class ClassificationController {

	private final ClassificationService service;

	ClassificationController(ClassificationService service) {
		this.service = service;
	}

	@PutMapping("/{transactionId}/allocations")
	List<Allocation> classify(@PathVariable UUID transactionId,
			@RequestBody List<@Nullable AllocationRequest> allocations) {
		return service.classify(transactionId, allocations);
	}

	/** Saves every transaction or none. The result has each transaction's new pieces, keyed by its id. */
	@PutMapping("/allocations")
	Map<UUID, List<Allocation>> classifyAll(@RequestBody BulkClassificationRequest request) {
		return service.classifyAll(request);
	}

}
