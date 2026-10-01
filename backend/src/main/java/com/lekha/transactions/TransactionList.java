package com.lekha.transactions;

import java.math.BigDecimal;
import java.util.List;

/**
 * Transactions newest first, with totals summed exactly on the server. Following the sign rule, money out is negative
 * and net is money in plus money out.
 */
record TransactionList(List<Transaction> transactions, BigDecimal moneyIn, BigDecimal moneyOut, BigDecimal net) {
}
