package com.petcuidar;

import com.petcuidar.migracao.MigracaoSqlitePostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MigracaoPostgresqlIT {

    private static final Map<String, Long> IDS = Map.of(
            "tutores", 11L, "veterinarios", 21L, "pets", 31L, "horarios_atendimento_veterinario", 41L,
            "bloqueios_agenda_veterinario", 51L, "consultas", 61L, "historico_atendimentos", 71L,
            "lembretes", 81L, "predicoes_no_show", 91L, "contas", 101L);

    @TempDir Path temporario;
    private Path origem;
    private PostgresqlTeste.Banco destino;

    @BeforeEach
    void preparar() throws Exception {
        origem = temporario.resolve("origem.sqlite");
        SqliteMigracaoTeste.criar(origem);
        destino = PostgresqlTeste.novoBanco();
    }

    @Test
    void preservaLinhasEReferenciasDeTodasAsTabelas() throws SQLException {
        alterarOrigem("UPDATE predicoes_no_show SET score_cru=0.6123456, score_usado=0.51234567");
        alterarOrigem("""
                INSERT INTO contas(id,email,nome,perfil,tutor_id)
                VALUES(102,'tutor@example.test','Tutor teste','TUTOR',11)
                """);
        Map<String, Long> contagens = migrar(ZoneOffset.UTC);
        JdbcTemplate jdbc = destino.jdbc();
        assertEquals(IDS.keySet(), contagens.keySet());
        IDS.forEach((tabela, id) -> {
            assertEquals(tabela.equals("contas") ? 2L : 1L, contagens.get(tabela));
            assertEquals(id, jdbc.queryForObject("SELECT min(id) FROM " + tabela, Long.class));
        });

        String vinculos = jdbc.queryForObject("""
                SELECT concat_ws(':', t.id, p.id, c.id, v.id, h.id, l.id, n.id, a.id, s.id, b.id)
                FROM tutores t JOIN pets p ON p.tutor_id=t.id
                JOIN consultas c ON c.pet_id=p.id
                JOIN veterinarios v ON v.id=c.veterinario_id
                JOIN historico_atendimentos h ON h.consulta_id=c.id AND h.pet_id=p.id AND h.veterinario_id=v.id
                JOIN lembretes l ON l.consulta_id=c.id
                JOIN predicoes_no_show n ON n.consulta_id=c.id
                JOIN contas a ON a.veterinario_id=v.id
                JOIN horarios_atendimento_veterinario s ON s.veterinario_id=v.id
                JOIN bloqueios_agenda_veterinario b ON b.veterinario_id=v.id
                """, String.class);
        assertEquals("11:31:61:21:71:81:91:101:41:51", vinculos);
        assertEquals("Descrição clínica teste", jdbc.queryForObject(
                "SELECT descricao FROM historico_atendimentos", String.class));
        assertEquals("REALIZADA", jdbc.queryForObject("SELECT status FROM consultas", String.class));
        assertEquals("VETERINARIO", jdbc.queryForObject("SELECT perfil FROM contas WHERE id=101", String.class));
        assertEquals(11L, jdbc.queryForObject("SELECT tutor_id FROM contas WHERE id=102", Long.class));
        assertEquals(21L, jdbc.queryForObject("SELECT veterinario_criacao_id FROM historico_atendimentos", Long.class));
        assertEquals(21L, jdbc.queryForObject("SELECT veterinario_atualizacao_id FROM historico_atendimentos", Long.class));
        assertEquals("08:00", jdbc.queryForObject("SELECT hora_inicio FROM horarios_atendimento_veterinario", String.class));
        assertEquals("Bloqueio teste", jdbc.queryForObject("SELECT motivo FROM bloqueios_agenda_veterinario", String.class));
        assertEquals("Lembrete final", jdbc.queryForObject("SELECT texto_validado FROM lembretes", String.class));
        assertEquals(new BigDecimal("0.6123456"), jdbc.queryForObject("SELECT score_cru FROM predicoes_no_show", BigDecimal.class));
        assertEquals(new BigDecimal("0.51234567"), jdbc.queryForObject("SELECT score_usado FROM predicoes_no_show", BigDecimal.class));
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT usou_fallback FROM predicoes_no_show", Boolean.class));
        assertNull(jdbc.queryForObject("SELECT ultimo_erro FROM lembretes", String.class));
        try (var sqlite = DriverManager.getConnection("jdbc:sqlite:" + origem.toAbsolutePath());
             var sql = sqlite.createStatement()) {
            for (String tabela : IDS.keySet()) {
                var salvos = jdbc.queryForList("SELECT * FROM " + tabela + " ORDER BY id");
                try (var colunaOrigem = sql.executeQuery("SELECT * FROM " + tabela + " ORDER BY id")) {
                    int indice = 0;
                    while (colunaOrigem.next()) {
                        var linha = salvos.get(indice++);
                        for (var coluna : linha.entrySet()) {
                            String nome = coluna.getKey();
                            Object esperado = colunaOrigem.getObject(nome);
                            Object valor = coluna.getValue();
                            if (valor instanceof java.sql.Timestamp data) {
                                valor = data.toLocalDateTime();
                                esperado = esperado instanceof Number epoch
                                        ? LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch.longValue()), ZoneOffset.UTC)
                                        : LocalDateTime.parse(((String) esperado).replace(' ', 'T'));
                            } else if (valor instanceof BigDecimal) {
                                esperado = new BigDecimal(colunaOrigem.getString(nome));
                            } else if (valor instanceof Boolean) {
                                esperado = colunaOrigem.getBoolean(nome);
                            } else if (valor instanceof Long && esperado instanceof Number numero) {
                                esperado = numero.longValue();
                            }
                            assertEquals(esperado, valor, tabela + "." + nome);
                        }
                    }
                    assertEquals(salvos.size(), indice, tabela);
                }
            }
        }
        System.out.println("PASS C5 carga preserva dados ids e vinculos");
    }

    @Test
    void converteEpochNoFusoDeOrigemEPreservaDatasTextuais() {
        ZoneId fuso = ZoneId.of("America/Sao_Paulo");
        LocalDateTime esperado = LocalDateTime.ofInstant(Instant.ofEpochMilli(SqliteMigracaoTeste.INSTANTE), fuso);
        migrar(fuso);

        JdbcTemplate jdbc = destino.jdbc();
        assertEquals(esperado, jdbc.queryForObject("SELECT data_hora FROM consultas", LocalDateTime.class));
        assertEquals(esperado, jdbc.queryForObject("SELECT inicio FROM bloqueios_agenda_veterinario", LocalDateTime.class));
        assertEquals(esperado.plusMinutes(30), jdbc.queryForObject("SELECT fim FROM bloqueios_agenda_veterinario", LocalDateTime.class));
        assertEquals(esperado, jdbc.queryForObject("SELECT data FROM historico_atendimentos", LocalDateTime.class));
        assertEquals(esperado.plusHours(1), jdbc.queryForObject("SELECT data_atualizacao FROM historico_atendimentos", LocalDateTime.class));
        assertEquals(LocalDateTime.of(2026, 9, 22, 11, 0), jdbc.queryForObject(
                "SELECT data_criacao FROM historico_atendimentos", LocalDateTime.class));
        assertEquals(esperado, jdbc.queryForObject("SELECT data_envio FROM lembretes", LocalDateTime.class));
        assertEquals(esperado, jdbc.queryForObject("SELECT data_ultima_tentativa FROM lembretes", LocalDateTime.class));
        assertEquals(esperado, jdbc.queryForObject("SELECT data_calculo FROM predicoes_no_show", LocalDateTime.class));

    }

    @Test
    void todasAsIdentidadesAvancamAposOsIdsImportados() {
        migrar(ZoneOffset.UTC);
        JdbcTemplate jdbc = destino.jdbc();
        assertEquals(22L, jdbc.queryForObject(
                "INSERT INTO veterinarios (nome) VALUES ('Nova veterinária') RETURNING id", Long.class));
        IDS.forEach((tabela, id) -> {
            if (!tabela.equals("veterinarios")) {
                assertEquals(id + 1, jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence(?, 'id'))", Long.class, tabela));
            }
        });
        System.out.println("PASS C7 novas identidades avancam apos a carga");
    }

    @Test
    void mantemContasEDeixaCredenciaisTemporariasNaOrigem() {
        migrar(ZoneOffset.UTC);
        JdbcTemplate jdbc = destino.jdbc();
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM contas", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM desafios_email", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM credenciais_auth", Long.class));
        assertEquals("1", jdbc.queryForObject("SELECT version FROM flyway_schema_history", String.class));
        System.out.println("PASS C8 destino inicia com historico proprio e sessoes vazias");
    }

    @Test
    void recusaOrigemInvalidaAntesDeCriarTabelas() throws Exception {
        alterarOrigem("UPDATE flyway_schema_history SET version='6' WHERE version='7'");
        assertThrows(MigracaoSqlitePostgres.Falha.class, () -> migrar(ZoneOffset.UTC));
        assertDestinoSemCatalogo();
        alterarOrigem("UPDATE flyway_schema_history SET version='7' WHERE description='normalizar emails de tutores'");
        alterarOrigem("UPDATE pets SET tutor_id=98765");
        var erro = assertThrows(MigracaoSqlitePostgres.Falha.class, () -> migrar(ZoneOffset.UTC));
        assertTrue(erro.getMessage().contains("referências inválidas"));
        assertDestinoSemCatalogo();
        alterarOrigem("UPDATE pets SET tutor_id=11");
        alterarOrigem("ALTER TABLE desafios_email RENAME COLUMN tentativas TO tentativas_fora_do_v7");
        erro = assertThrows(MigracaoSqlitePostgres.Falha.class, () -> migrar(ZoneOffset.UTC));
        assertTrue(erro.getMessage().contains("catálogo da origem"));
        assertDestinoSemCatalogo();

        java.nio.file.Files.writeString(origem, "arquivo corrompido");
        assertThrows(MigracaoSqlitePostgres.Falha.class, () -> migrar(ZoneOffset.UTC));
        assertDestinoSemCatalogo();
        System.out.println("PASS C10 recusa origem invalida antes da carga");
    }

    @Test
    void destinoComRegistroPreservaSeusDados() {
        destino.migrar();
        destino.jdbc().update("INSERT INTO veterinarios (id, nome) VALUES (55, 'Já existente')");
        assertThrows(MigracaoSqlitePostgres.Falha.class, () -> migrar(ZoneOffset.UTC));
        assertEquals("Já existente", destino.jdbc().queryForObject("SELECT nome FROM veterinarios", String.class));
        assertEquals(0L, destino.jdbc().queryForObject("SELECT count(*) FROM tutores", Long.class));
        destino.jdbc().update("DELETE FROM veterinarios");
        destino.jdbc().update("""
                INSERT INTO contas (id, email, nome, perfil)
                VALUES (56, 'admin@example.test', 'Admin anterior', 'ADMIN')
                """);
        var erro = assertThrows(MigracaoSqlitePostgres.Falha.class, () -> migrar(ZoneOffset.UTC));
        assertTrue(erro.getMessage().contains("contas"));
        assertEquals("admin@example.test", destino.jdbc().queryForObject("SELECT email FROM contas WHERE id = 56", String.class));
        assertEquals(1L, destino.jdbc().queryForObject("SELECT count(*) FROM contas", Long.class));
        IDS.keySet().stream().filter(tabela -> !tabela.equals("contas")).forEach(tabela ->
                assertEquals(0L, destino.jdbc().queryForObject("SELECT count(*) FROM " + tabela, Long.class)));
        System.out.println("PASS C11 recusa destino com registros existentes");
    }

    @Test
    void erroTardioReverteTodasAsTabelasEIdentidades() {
        destino.migrar();
        var jdbc = destino.jdbc();
        jdbc.execute("ALTER TABLE contas ADD CONSTRAINT teste_recusa_veterinario CHECK (perfil <> 'VETERINARIO')");
        var erro = assertThrows(MigracaoSqlitePostgres.Falha.class, () -> migrar(ZoneOffset.UTC));
        assertTrue(erro.getMessage().contains("tabela contas"));
        IDS.keySet().forEach(tabela ->
                assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM " + tabela, Long.class)));
        assertEquals(1L, jdbc.queryForObject("INSERT INTO veterinarios(nome) VALUES ('Teste') RETURNING id", Long.class));
        System.out.println("PASS C12 falha desfaz toda a carga");
    }

    private Map<String, Long> migrar(ZoneId fuso) {
        return MigracaoSqlitePostgres.importar(origem, fuso, destino.url(), destino.usuario(), destino.senha());
    }

    private void alterarOrigem(String comando) throws SQLException {
        try (var banco = DriverManager.getConnection("jdbc:sqlite:" + origem.toAbsolutePath());
             var sql = banco.createStatement()) {
            sql.executeUpdate(comando);
        }
    }

    private void assertDestinoSemCatalogo() {
        assertEquals(0L, destino.jdbc().queryForObject("""
                SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema()
                """, Long.class));
    }
}
