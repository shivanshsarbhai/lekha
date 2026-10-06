package com.lekha.transactions;

import java.math.BigDecimal;
import java.util.List;

/**
 * Transactions newest first, with totals summed exactly on the server.
 * <p>
 * Cash flow ({@code moneyIn}, {@code moneyOut}, {@code net}) mirrors the bank: money out is negative and transfers
 * count. The classified totals answer "where did my money go" and are positive in the usual case: {@code spending}
 * is expenses net of refunds, {@code invested} and {@code lent} are net of money coming back, and transfers count
 * towards none of them.
 */
record TransactionList(List<ListedTransaction> transactions, BigDecimal moneyIn, BigDecimal moneyOut, BigDecimal net,
		BigDecimal spending, BigDecimal income, BigDecimal invested, BigDecimal lent, int unclassifiedCount) {
}
