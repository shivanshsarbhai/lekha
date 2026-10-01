package com.lekha.categories;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
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

	private static Category mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new Category(
				rs.getObject("id", UUID.class),
				rs.getObject("parent_id", UUID.class),
				rs.getString("name"),
				CategoryKind.valueOf(rs.getString("kind")),
				rs.getObject("created_at", OffsetDateTime.class).toInstant());
	}

}
