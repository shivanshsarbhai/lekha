package com.lekha.transactions;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
class TransactionController {

	private final TransactionService service;

	TransactionController(TransactionService service) {
		this.service = service;
	}

	@GetMapping
	TransactionList list(@RequestParam(required = false) @Nullable UUID accountId,
			@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
		return service.list(accountId, from, to);
	}

	@GetMapping("/{id}/refund-candidates")
	List<RefundCandidate> refundCandidates(@PathVariable UUID id, @RequestParam(required = false) @Nullable String search) {
		return service.refundCandidates(id, search);
	}

	@PutMapping("/{id}")
	ListedTransaction update(@PathVariable UUID id, @RequestBody UpdateTransactionRequest request) {
		return service.update(id, request.transactionDate(), request.description(), request.amount());
	}

}
