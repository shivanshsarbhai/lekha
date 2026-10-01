package com.lekha.accounts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AccountRepository {

	private final JdbcClient jdbc;

	AccountRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	List<Account> findAll() {
		return jdbc.sql("""
				SELECT id, nickname, type, institution, last4, created_at
				FROM accounts
				ORDER BY created_at, id
				""")
				.query(AccountRepository::mapRow)
				.list();
	}

	private static Account mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new Account(
				rs.getObject("id", UUID.class),
				rs.getString("nickname"),
				AccountType.valueOf(rs.getString("type")),
				Institution.valueOf(rs.getString("institution")),
				rs.getString("last4"),
				rs.getObject("created_at", OffsetDateTime.class).toInstant());
	}
}
