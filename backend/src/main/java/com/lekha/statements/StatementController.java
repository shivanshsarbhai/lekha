package com.lekha.statements;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

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

	@PostMapping
	StatementImport upload(@PathVariable UUID accountId, @RequestParam MultipartFile file) throws IOException {
		try (InputStream content = file.getInputStream()) {
			return service.importStatement(accountId, content);
		}
	}

}
