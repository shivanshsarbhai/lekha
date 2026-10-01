package com.lekha.statements;

import java.io.InputStream;
import java.util.List;

import com.lekha.accounts.AccountType;
import com.lekha.accounts.Institution;

/** Reads one kind of statement file, such as an HDFC savings account PDF. */
interface StatementParser {

	/** Whether this parser reads statements for accounts of this institution and type. */
	boolean supports(Institution institution, AccountType type);

	/**
	 * Reads every transaction in the statement, oldest first.
	 * @throws StatementFormatException if the file is not a statement this parser understands
	 */
	List<StatementEntry> parse(InputStream file);

}
