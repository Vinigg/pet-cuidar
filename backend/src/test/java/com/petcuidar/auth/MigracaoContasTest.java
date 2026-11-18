package com.petcuidar.auth;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigracaoContasTest {
    @TempDir
    Path diretorio;

    @Test
    void migraApenasEmailsInequivocosEPreservaCadastroExistente() throws SQLException {
        String url = "jdbc:sqlite:" + diretorio.resolve("contas.db").toAbsolutePath();
        Flyway.configure().dataSource(url, null, null).target("5").load().migrate();

        try (var conexao = DriverManager.getConnection(url); var sql = conexao.createStatement()) {
            sql.executeUpdate("""
                    INSERT INTO tutores (id, nome, email) VALUES
                        (10, 'Ana', ' Ana@Exemplo.com '),
                        (11, 'Bia', 'Bia@Exemplo.com'),
                        (12, 'Beatriz', 'bia@exemplo.com'),
                        (13, 'Carla', 'carla@Éxemplo.invalid'),
                        (14, 'Carla duplicada', 'carla@éxemplo.invalid'),
                        (15, 'Daniel', 'daniel@Éxemplo.invalid')
                    """);
        }

        Flyway.configure().dataSource(url, null, null).load().migrate();
        try (var conexao = DriverManager.getConnection(url); var sql = conexao.createStatement()) {
            try (var contas = sql.executeQuery("SELECT email, nome, perfil, tutor_id FROM contas ORDER BY tutor_id")) {
                assertTrue(contas.next());
                assertEquals("ana@exemplo.com", contas.getString("email"));
                assertEquals("Ana", contas.getString("nome"));
                assertEquals("TUTOR", contas.getString("perfil"));
                assertEquals(10, contas.getLong("tutor_id"));
                assertTrue(contas.next());
                assertEquals("daniel@éxemplo.invalid", contas.getString("email"));
                assertEquals(15, contas.getLong("tutor_id"));
                assertFalse(contas.next());
            }
            try (var tutores = sql.executeQuery("SELECT count(*) FROM tutores")) {
                assertEquals(6, tutores.getInt(1));
            }
            assertThrows(SQLException.class, () -> sql.executeUpdate("""
                    INSERT INTO contas (email, nome, perfil, tutor_id)
                    VALUES ('outra@exemplo.com', 'Outra conta', 'ADMIN', 10)
                    """));
            assertThrows(SQLException.class, () -> sql.executeUpdate("""
                    INSERT INTO contas (email, nome, perfil)
                    VALUES ('ana@exemplo.com', 'Outra conta', 'ADMIN')
                    """));
        }
        System.out.println("PASS C27 migração preserva e-mails legados inequívocos");
    }
}
