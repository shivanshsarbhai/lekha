package com.lekha.categories;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;

import com.lekha.TestcontainersConfiguration;

@JdbcTest
@Import({ TestcontainersConfiguration.class, CategoryRepository.class, CategoryService.class })
class CategoryServiceTest {

	@Autowired
	private CategoryService service;

	@Test
	void treeGroupsKindsInOrderWithSubCategoriesUnderTheirParent() {
		List<CategoryNode> tree = service.tree();

		assertThat(tree).extracting(CategoryNode::kind).isSortedAccordingTo(Comparator.naturalOrder());
		assertThat(tree).extracting(CategoryNode::name).contains("Food", "Salary", "Mutual funds")
			.doesNotContain("Dining out");

		CategoryNode food = tree.stream().filter(node -> node.name().equals("Food")).findFirst().orElseThrow();
		assertThat(food.children()).extracting(CategoryNode::name)
			.containsExactly("Dining out", "Groceries", "Ordering in");
		assertThat(food.children()).allSatisfy(child -> {
			assertThat(child.kind()).isEqualTo(CategoryKind.EXPENSE);
			assertThat(child.children()).isEmpty();
		});
	}

	@Test
	void topLevelCategoriesWithinAKindAreAlphabetical() {
		List<String> incomeNames = service.tree()
			.stream()
			.filter(node -> node.kind() == CategoryKind.INCOME)
			.map(CategoryNode::name)
			.toList();

		assertThat(incomeNames).containsExactly("Dividends", "Freelance", "Gifts received", "Interest", "Salary");
	}

}
