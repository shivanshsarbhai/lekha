package com.lekha.statements;

import java.util.List;

import com.lekha.accounts.AccountType;
import com.lekha.accounts.Institution;

/** Reads one kind of statement, such as an HDFC savings account PDF, from the text extracted from it. */
interface StatementParser {

	/** Whether this parser reads statements for accounts of this institution and type. */
	boolean supports(Institution institution, AccountType type);

	/** The kind of account it reads, with its article, for messages: "an HDFC credit card". */
	String accountKind();

	/**
	 * A quick check that the text is this kind of statement, from a marker no other supported statement prints. It
	 * says nothing about whether the statement will parse.
	 */
	boolean recognises(List<String> lines);

	/**
	 * Reads every transaction in the statement, oldest first.
	 * @throws StatementFormatException if the text is not a statement this parser understands
	 */
	List<StatementEntry> parseText(List<String> lines);

}
