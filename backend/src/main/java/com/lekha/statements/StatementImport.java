package com.lekha.statements;

import java.util.List;

import com.lekha.transactions.Transaction;

/** What happened to a statement: the transactions newly saved, and how many were already stored. */
record StatementImport(List<Transaction> imported, int skipped) {
}
