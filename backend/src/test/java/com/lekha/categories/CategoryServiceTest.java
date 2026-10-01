package com.lekha.categories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.lekha.TestcontainersConfiguration;

@JdbcTest
@Import({ TestcontainersConfiguration.class, CategoryRepository.class, CategoryService.class })
class CategoryServiceTest {

	@Autowired
	private CategoryService service;

	@Autowired
	private CategoryRepository repository;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void treeGroupsKindsInOrderWithSubCategoriesUnderTheirParent() {
		List<CategoryNode> tree = service.tree();

		assertThat(tree).extracting(CategoryNode::kind).isSortedAccordingTo(Comparator.naturalOrder());
		assertThat(tree).extracting(CategoryNode::name).contains("Food", "Salary", "Mutual funds")
			.doesNotContain("Dining out");

		CategoryNode food = node(tree, "Food");
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

	@Test
	void createAddsATopLevelCategoryWithTheGivenKind() {
		Category pets = service.create("  Pets ", CategoryKind.EXPENSE, null);

		assertThat(pets.name()).isEqualTo("Pets");
		assertThat(pets.parentId()).isNull();
		assertThat(repository.findById(pets.id())).contains(pets);
	}

	@Test
	void createGivesASubCategoryItsParentsKind() {
		UUID food = id("Food");

		Category snacks = service.create("Snacks", null, food);

		assertThat(snacks.parentId()).isEqualTo(food);
		assertThat(snacks.kind()).isEqualTo(CategoryKind.EXPENSE);
	}

	@Test
	void createRejectsATopLevelCategoryWithoutAKind() {
		assertThatThrownBy(() -> service.create("Pets", null, null)).isInstanceOf(InvalidCategoryException.class)
			.hasMessage("A top-level category needs a kind: EXPENSE, INCOME or INVESTMENT");
	}

	@Test
	void createRejectsAThirdLevel() {
		UUID dining = id("Dining out");

		assertThatThrownBy(() -> service.create("Cafés", null, dining)).isInstanceOf(InvalidCategoryException.class)
			.hasMessage("\"Dining out\" is already a sub-category, so it can't have its own");
	}

	@Test
	void createRejectsASubCategoryWithADifferentKind() {
		UUID food = id("Food");

		assertThatThrownBy(() -> service.create("Bonus", CategoryKind.INCOME, food))
			.isInstanceOf(InvalidCategoryException.class)
			.hasMessage("A sub-category of \"Food\" must also be EXPENSE");
	}

	@Test
	void createRejectsAnUnknownParent() {
		UUID unknown = UUID.randomUUID();

		assertThatThrownBy(() -> service.create("Snacks", null, unknown)).isInstanceOf(InvalidCategoryException.class)
			.hasMessage("No category with id " + unknown + " to add this under");
	}

	@Test
	void createRejectsABlankOrMissingName() {
		assertThatThrownBy(() -> service.create("   ", CategoryKind.EXPENSE, null))
			.isInstanceOf(InvalidCategoryException.class)
			.hasMessage("Category name must not be blank");
		assertThatThrownBy(() -> service.create(null, CategoryKind.EXPENSE, null))
			.isInstanceOf(InvalidCategoryException.class)
			.hasMessage("Category name must not be blank");
	}

	@Test
	void createRejectsADuplicateSiblingWithAFriendlyMessage() {
		assertThatThrownBy(() -> service.create("groceries", null, id("Food")))
			.isInstanceOf(DuplicateCategoryNameException.class)
			.hasMessage("There is already a category called \"groceries\" here")
			.hasCauseInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void renameChangesOnlyTheName() {
		Category dining = find("Dining out");

		Category renamed = service.rename(dining.id(), "Restaurants");

		assertThat(renamed).isEqualTo(new Category(dining.id(), dining.parentId(), "Restaurants", dining.kind(),
				dining.createdAt()));
		assertThat(repository.findById(dining.id())).contains(renamed);
	}

	@Test
	void renameRejectsAnUnknownCategory() {
		UUID unknown = UUID.randomUUID();

		assertThatThrownBy(() -> service.rename(unknown, "Anything")).isInstanceOf(CategoryNotFoundException.class)
			.hasMessage("No category with id " + unknown);
	}

	@Test
	void renameRejectsANameASiblingAlreadyHas() {
		assertThatThrownBy(() -> service.rename(id("Dining out"), "Groceries"))
			.isInstanceOf(DuplicateCategoryNameException.class);
	}

	@Test
	void deleteRemovesACategoryWithoutSubCategories() {
		UUID movies = id("Movies");

		service.delete(movies);

		assertThat(repository.findById(movies)).isEmpty();
	}

	@Test
	void deleteRefusesACategoryThatHasSubCategories() {
		assertThatThrownBy(() -> service.delete(id("Food"))).isInstanceOf(CategoryInUseException.class)
			.hasMessage("\"Food\" has sub-categories. Delete or rename those first.");
	}

	@Test
	void deleteRefusesACategoryUsedByAnAllocation() {
		UUID movies = id("Movies");
		classifySomethingAs(movies);

		assertThatThrownBy(() -> service.delete(movies)).isInstanceOf(CategoryInUseException.class)
			.hasMessage("\"Movies\" is used by classified transactions. Re-classify them first.");
	}

	@Test
	void deleteRejectsAnUnknownCategory() {
		assertThatThrownBy(() -> service.delete(UUID.randomUUID())).isInstanceOf(CategoryNotFoundException.class);
	}

	/** Raw SQL, because accounts, transactions and allocations belong to other packages. */
	private void classifySomethingAs(UUID categoryId) {
		UUID account = UUID.randomUUID();
		UUID transaction = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO accounts (id, nickname, type, institution, last4, created_at)
				VALUES (:id, 'HDFC Salary', 'BANK', 'HDFC', NULL, now())
				""").param("id", account).update();
		jdbc.sql("""
				INSERT INTO transactions (id, account_id, transaction_date, description, amount, metadata, created_at)
				VALUES (:id, :account, DATE '2026-09-01', 'BOOKMYSHOW', -600.00, '{}'::jsonb, now())
				""").param("id", transaction).param("account", account).update();
		jdbc.sql("""
				INSERT INTO allocations (id, transaction_id, kind, category_id, amount)
				VALUES (:id, :transaction, 'EXPENSE', :category, -600.00)
				""").param("id", UUID.randomUUID()).param("transaction", transaction).param("category", categoryId).update();
	}

	private Category find(String name) {
		return repository.findAll().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
	}

	private UUID id(String name) {
		return find(name).id();
	}

	private static CategoryNode node(List<CategoryNode> tree, String name) {
		return tree.stream().filter(node -> node.name().equals(name)).findFirst().orElseThrow();
	}

}
