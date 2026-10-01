package com.lekha.transactions;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

	private final TransactionRepository repository;

	TransactionService(TransactionRepository repository) {
		this.repository = repository;
	}

	/** Saves all the transactions, or none of them if any one fails. */
	@Transactional
	public void recordAll(List<Transaction> transactions) {
		transactions.forEach(repository::insert);
	}

}
