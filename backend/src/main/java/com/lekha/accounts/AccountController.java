package com.lekha.accounts;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
class AccountController {

	private final AccountService service;

	AccountController(AccountService service) {
		this.service = service;
	}

	@GetMapping
	List<Account> listAccounts() {
		return service.listAccounts();
	}

	@GetMapping("/{id}")
	ResponseEntity<Account> getAccount(@PathVariable UUID id) {
		return ResponseEntity.of(service.findAccount(id));
	}

	@PostMapping
	ResponseEntity<Account> createAccount(@RequestBody CreateAccountRequest request) {
		Account account = service.createAccount(request.nickname(), request.type(), request.institution(),
				request.last4());
		return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.id())).body(account);
	}
}
