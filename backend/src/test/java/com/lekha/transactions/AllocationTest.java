package com.lekha.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AllocationTest {

	private static final UUID TRANSACTION = UUID.randomUUID();

	private static final UUID CATEGORY = UUID.randomUUID();

	@Test
	void normalisesTheAmountAndNote() {
		Allocation allocation = Allocation.create(TRANSACTION, AllocationKind.EXPENSE, CATEGORY, new BigDecimal("-500"),
				"  Rahul's birthday  ");

		assertThat(allocation.amount()).isEqualTo(new BigDecimal("-500.00"));
		assertThat(allocation.note()).isEqualTo("Rahul's birthday");
	}

	@Test
	void turnsABlankNoteIntoNoNote() {
		assertThat(allocation(AllocationKind.LENT, null, "-1500.00", "   ").note()).isNull();
	}

	@Test
	void rejectsAZeroAmount() {
		assertThatThrownBy(() -> allocation(AllocationKind.EXPENSE, null, "0.00", null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Allocation amount must not be zero");
	}

	@Test
	void rejectsMoreThanTwoDecimals() {
		assertThatThrownBy(() -> allocation(AllocationKind.EXPENSE, null, "-10.005", null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageStartingWith("Money must have at most 2 decimal places");
	}

	@Test
	void rejectsANoteOverTheLimit() {
		assertThat(allocation(AllocationKind.EXPENSE, null, "-1.00", "x".repeat(200)).note()).hasSize(200);
		assertThatThrownBy(() -> allocation(AllocationKind.EXPENSE, null, "-1.00", "x".repeat(201)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Note must be at most 200 characters");
	}

	@ParameterizedTest
	@EnumSource(value = AllocationKind.class, names = { "TRANSFER", "LENT" })
	void rejectsACategoryOnKindsThatHaveNone(AllocationKind kind) {
		assertThatThrownBy(() -> allocation(kind, CATEGORY, "-100.00", null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage(kind + " allocations can't have a category");
	}

	@ParameterizedTest
	@EnumSource(value = AllocationKind.class, names = { "EXPENSE", "INCOME", "INVESTMENT" })
	void allowsACategoryOnKindsThatHaveThem(AllocationKind kind) {
		assertThat(allocation(kind, CATEGORY, "-100.00", null).categoryId()).isEqualTo(CATEGORY);
	}

	@Test
	void displayOrderIsKindThenLargerAmountFirst() {
		Allocation smallExpense = allocation(AllocationKind.EXPENSE, null, "-100.00", null);
		Allocation largeExpense = allocation(AllocationKind.EXPENSE, null, "-900.00", null);
		Allocation lent = allocation(AllocationKind.LENT, null, "-1500.00", null);
		List<Allocation> allocations = new ArrayList<>(List.of(lent, smallExpense, largeExpense));

		allocations.sort(Allocation.DISPLAY_ORDER);

		assertThat(allocations).containsExactly(largeExpense, smallExpense, lent);
	}

	private static Allocation allocation(AllocationKind kind, UUID categoryId, String amount, String note) {
		return Allocation.create(TRANSACTION, kind, categoryId, new BigDecimal(amount), note);
	}

}
