package com.lekha.accounts;

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

	Optional<Account> findById(UUID id) {
		return jdbc.sql("""
				SELECT id, nickname, type, institution, last4, created_at
				FROM accounts
				WHERE id = :id
				""")
				.param("id", id)
				.query(AccountRepository::mapRow)
				.optional();
	}

	void insert(Account account) {
		jdbc.sql("""
				INSERT INTO accounts (id, nickname, type, institution, last4, created_at)
				VALUES (:id, :nickname, :type, :institution, :last4, :createdAt)
				""")
				.param("id", account.id())
				.param("nickname", account.nickname())
				.param("type", account.type().name())
				.param("institution", account.institution().name())
				.param("last4", account.last4())
				.param("createdAt", OffsetDateTime.ofInstant(account.createdAt(), ZoneOffset.UTC))
				.update();
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
