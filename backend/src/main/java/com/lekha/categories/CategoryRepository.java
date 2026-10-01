package com.lekha.categories;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CategoryRepository {

	private final JdbcClient jdbc;

	CategoryRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	List<Category> findAll() {
		return jdbc.sql("""
				SELECT id, parent_id, name, kind, created_at
				FROM categories
				ORDER BY lower(name), id
				""")
				.query(CategoryRepository::mapRow)
				.list();
	}

	Optional<Category> findById(UUID id) {
		return jdbc.sql("""
				SELECT id, parent_id, name, kind, created_at
				FROM categories
				WHERE id = :id
				""")
				.param("id", id)
				.query(CategoryRepository::mapRow)
				.optional();
	}

	boolean hasChildren(UUID id) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM categories WHERE parent_id = :id)")
				.param("id", id)
				.query(Boolean.class)
				.single();
	}

	void insert(Category category) {
		jdbc.sql("""
				INSERT INTO categories (id, parent_id, name, kind, created_at)
				VALUES (:id, :parentId, :name, :kind, :createdAt)
				""")
				.param("id", category.id())
				.param("parentId", category.parentId())
				.param("name", category.name())
				.param("kind", category.kind().name())
				.param("createdAt", OffsetDateTime.ofInstant(category.createdAt(), ZoneOffset.UTC))
				.update();
	}

	void rename(UUID id, String name) {
		jdbc.sql("UPDATE categories SET name = :name WHERE id = :id")
				.param("id", id)
				.param("name", name)
				.update();
	}

	void delete(UUID id) {
		jdbc.sql("DELETE FROM categories WHERE id = :id").param("id", id).update();
	}

	private static Category mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new Category(
				rs.getObject("id", UUID.class),
				rs.getObject("parent_id", UUID.class),
				rs.getString("name"),
				CategoryKind.valueOf(rs.getString("kind")),
				rs.getObject("created_at", OffsetDateTime.class).toInstant());
	}

}
