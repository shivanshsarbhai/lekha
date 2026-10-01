package com.lekha.transactions;

/** How money moved. Values must match the payment_mode CHECK constraint in V2. */
public enum PaymentMode {
	UPI,
	CARD,
	NEFT,
	IMPS,
	RTGS,
	ATM,
	CHEQUE,
	OTHER
}
