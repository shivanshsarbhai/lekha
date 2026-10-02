package com.lekha.transactions;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions/{transactionId}/allocations")
class ClassificationController {

	private final ClassificationService service;

	ClassificationController(ClassificationService service) {
		this.service = service;
	}

	@PutMapping
	List<Allocation> classify(@PathVariable UUID transactionId,
			@RequestBody List<@Nullable AllocationRequest> allocations) {
		return service.classify(transactionId, allocations);
	}

}
