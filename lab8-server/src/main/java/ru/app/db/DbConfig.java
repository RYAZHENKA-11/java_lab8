package ru.app.db;

/**
 * Database connection configuration.
 *
 * @param url JDBC connection URL (e.g. {@code jdbc:postgresql://localhost:5432/mydb})
 * @param user database user name
 * @param password database password
 */
public record DbConfig(String url, String user, String password) {}
