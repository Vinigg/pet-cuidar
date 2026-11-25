package com.petcuidar;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EsquemaPostgresqlIT {

    @Test
    void aplicaCatalogoComTiposEIntegridade() throws Exception {
        var banco = PostgresqlTeste.novoBanco();
        banco.migrar();
        var jdbc = banco.jdbc();

        assertEquals(Set.of("tutores", "pets", "veterinarios", "horarios_atendimento_veterinario",
                        "bloqueios_agenda_veterinario", "consultas", "historico_atendimentos", "lembretes",
                        "predicoes_no_show", "contas", "credenciais_auth", "desafios_email", "flyway_schema_history"),
                Set.copyOf(jdbc.queryForList("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = current_schema()
                        """, String.class)));
        assertEquals("1", jdbc.queryForObject("SELECT version FROM flyway_schema_history", String.class));
        assertEquals("boolean", tipo(jdbc, "veterinarios", "ativo"));
        assertEquals("timestamp without time zone", tipo(jdbc, "consultas", "data_hora"));
        assertEquals("numeric", tipo(jdbc, "predicoes_no_show", "score_cru"));

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO pets (tutor_id, nome) VALUES (1234, 'Teste')"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO contas (email, nome, perfil) VALUES ('teste@example.test', 'Teste', 'TUTOR')
                """));
    }

    private String tipo(org.springframework.jdbc.core.JdbcTemplate jdbc, String tabela, String coluna) {
        return jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                """, String.class, tabela, coluna);
    }
}
