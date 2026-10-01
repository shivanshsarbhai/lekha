package com.lekha.categories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.lekha.TestcontainersConfiguration;

@JdbcTest
@Import({ TestcontainersConfiguration.class, CategoryRepository.class })
class CategoryRepositoryTest {

	@Autowired
	private CategoryRepository repository;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void migrationSeedsTheStartingSet() {
		List<Category> all = repository.findAll();
		Category food = find(all, "Food");
		Category dining = find(all, "Dining out");

		assertThat(food.parentId()).isNull();
		assertThat(food.kind()).isEqualTo(CategoryKind.EXPENSE);
		assertThat(dining.parentId()).isEqualTo(food.id());
		assertThat(dining.kind()).isEqualTo(CategoryKind.EXPENSE);
		assertThat(find(all, "Salary").kind()).isEqualTo(CategoryKind.INCOME);
		assertThat(find(all, "Mutual funds").kind()).isEqualTo(CategoryKind.INVESTMENT);
	}

	@Test
	void databaseRejectsADuplicateTopLevelNameIgnoringCase() {
		assertThatThrownBy(() -> insert(null, "food", "EXPENSE")).isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void databaseRejectsADuplicateSubCategoryNameIgnoringCase() {
		UUID food = find(repository.findAll(), "Food").id();

		assertThatThrownBy(() -> insert(food, "GROCERIES", "EXPENSE")).isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void databaseAllowsTheSameNameUnderDifferentParentsOrKinds() {
		List<Category> all = repository.findAll();
		UUID food = find(all, "Food").id();
		UUID travel = find(all, "Travel").id();

		assertThatCode(() -> {
			insert(food, "Other", "EXPENSE");
			insert(travel, "Other", "EXPENSE");
			insert(null, "Other", "EXPENSE");
			insert(null, "Other", "INCOME");
		}).doesNotThrowAnyException();
	}

	@Test
	void databaseRejectsAnUnknownKind() {
		assertThatThrownBy(() -> insert(null, "Rahul", "LENT")).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("categories_kind_check");
	}

	private static Category find(List<Category> categories, String name) {
		return categories.stream().filter(category -> category.name().equals(name)).findFirst().orElseThrow();
	}

	private void insert(@Nullable UUID parentId, String name, String kind) {
		jdbc.sql("""
				INSERT INTO categories (id, parent_id, name, kind, created_at)
				VALUES (:id, :parentId, :name, :kind, now())
				""")
				.param("id", UUID.randomUUID())
				.param("parentId", parentId)
				.param("name", name)
				.param("kind", kind)
				.update();
	}

}
