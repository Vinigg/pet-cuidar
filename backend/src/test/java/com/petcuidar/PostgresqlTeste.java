package com.petcuidar;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

final class PostgresqlTeste {

    private PostgresqlTeste() {
    }

    static Banco novoBanco() throws SQLException {
        String base = ambiente("PETCUIDAR_TEST_POSTGRES_URL");
        URI uri = URI.create(base.substring("jdbc:".length()));
        if (!"postgresql".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost())
                || !"/petcuidar".equals(uri.getPath())) {
            throw new IllegalArgumentException("Use o PostgreSQL local do script de testes.");
        }

        String usuario = ambiente("PETCUIDAR_TEST_POSTGRES_USER");
        String senha = ambiente("PETCUIDAR_TEST_POSTGRES_PASSWORD");
        String schema = "teste_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(base, usuario, senha);
             var sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
        }
        return new Banco(base + "&currentSchema=" + schema, usuario, senha, schema);
    }

    private static String ambiente(String nome) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Execute backend/scripts/test-postgresql.py.");
        }
        return valor;
    }

    record Banco(String url, String usuario, String senha, String schema) {
        Connection conectar() throws SQLException {
            return DriverManager.getConnection(url, usuario, senha);
        }

        JdbcTemplate jdbc() {
            return new JdbcTemplate(new DriverManagerDataSource(url, usuario, senha));
        }

        void migrar() {
            Flyway.configure().dataSource(url, usuario, senha)
                    .locations("classpath:db/postgresql")
                    .load().migrate();
        }
    }
}
