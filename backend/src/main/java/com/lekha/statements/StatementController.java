package com.lekha.statements;

import java.io.IOException;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/statements")
class StatementController {

	private final StatementImportService service;

	StatementController(StatementImportService service) {
		this.service = service;
	}

	/** {@code password} is only needed for a locked PDF. It is sent as a form field, never in the URL. */
	@PostMapping
	StatementImport upload(@PathVariable UUID accountId, @RequestParam MultipartFile file,
			@RequestParam(required = false) @Nullable String password) throws IOException {
		return service.importStatement(accountId, file.getBytes(), password);
	}

}
