package com.petcuidar.migracao;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.math.BigDecimal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.stream.Stream;

public final class MigracaoSqlitePostgres {

    private static final List<Tabela> TABELAS = List.of(
            new Tabela("tutores", "id, nome, email, telefone, cpf, email_normalizado"),
            new Tabela("veterinarios", "id, nome, especialidade, versao, ativo"),
            new Tabela("pets", "id, tutor_id, nome, especie, idade, alergias_criticas"),
            new Tabela("horarios_atendimento_veterinario", "id, veterinario_id, dia_semana, hora_inicio, hora_fim"),
            new Tabela("bloqueios_agenda_veterinario", "id, veterinario_id, inicio, fim, motivo"),
            new Tabela("consultas", "id, pet_id, veterinario_id, data_hora, status"),
            new Tabela("historico_atendimentos", """
                    id, pet_id, consulta_id, descricao, resumo_ia, data, veterinario_id,
                    data_criacao, data_atualizacao, veterinario_criacao_id, veterinario_atualizacao_id, versao
                    """.strip()),
            new Tabela("lembretes", """
                    id, consulta_id, canal, texto_gerado_ia, texto_validado, status_envio, data_envio, tentativas,
                    ultimo_erro, data_ultima_tentativa, usou_fallback
                    """.strip()),
            new Tabela("predicoes_no_show", "id, consulta_id, score_cru, score_usado, usou_fallback, motivo, data_calculo"),
            new Tabela("contas", "id, email, nome, perfil, tutor_id, veterinario_id"));

    private static final List<String> VERIFICAR_DESTINO = Stream.concat(
            TABELAS.stream().map(Tabela::nome), Stream.of("desafios_email", "credenciais_auth")).toList();

    private MigracaoSqlitePostgres() {
    }

    public static Map<String, Long> importar(Path arquivo, ZoneId fusoOrigem,
                                              String url, String usuario, String senha) {
        if (!Files.isRegularFile(arquivo)) {
            throw new Falha("A cópia SQLite de origem não foi encontrada.");
        }
        if (url == null || !url.startsWith("jdbc:postgresql:")) {
            throw new Falha("Configure uma URL JDBC de destino PostgreSQL.");
        }

        String urlSqlite = "jdbc:sqlite:" + arquivo.toAbsolutePath().toUri().toASCIIString() + "?mode=ro";
        try (Connection origem = DriverManager.getConnection(urlSqlite)) {
            origem.setAutoCommit(false);
            validarOrigem(origem);
            prepararCatalogo(url, usuario, senha);
            try (Connection destino = DriverManager.getConnection(url, usuario, senha)) {
                return transferir(origem, destino, fusoOrigem);
            } catch (SQLException erro) {
                throw erroSql("Falha ao acessar o PostgreSQL.", erro);
            }
        } catch (SQLException erro) {
            throw erroSql("Falha ao ler a cópia SQLite V7.", erro);
        }
    }

    private static void validarOrigem(Connection origem) throws SQLException {
        try (var sql = origem.createStatement()) {
            try (var integridade = sql.executeQuery("PRAGMA integrity_check")) {
                if (!integridade.next() || !"ok".equals(integridade.getString(1))) {
                    throw new Falha("A cópia SQLite falhou na verificação de integridade.");
                }
            }
            try (var chaves = sql.executeQuery("PRAGMA foreign_key_check")) {
                if (chaves.next()) {
                    throw new Falha("A cópia SQLite contém referências inválidas.");
                }
            }
            try (var versao = sql.executeQuery("""
                    SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1
                    """)) {
                if (!versao.next() || !"7".equals(versao.getString(1)) || !versao.getBoolean(2)) {
                    throw new Falha("A origem deve estar no esquema SQLite V7.");
                }
            }
        }

        try (Connection referencia = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            Flyway.configure().dataSource(new SingleConnectionDataSource(referencia, true))
                    .locations("classpath:db/migration")
                    .target("7")
                    .load().migrate();
            if (!catalogoSqlite(referencia).equals(catalogoSqlite(origem))) {
                throw new Falha("O catálogo da origem não corresponde ao esquema SQLite V7.");
            }
        }
    }

    private static Map<String, String> catalogoSqlite(Connection banco) throws SQLException {
        Map<String, String> catalogo = new LinkedHashMap<>();
        try (var sql = banco.createStatement(); var registros = sql.executeQuery("""
                SELECT type, name, sql FROM sqlite_schema
                WHERE name NOT GLOB 'sqlite_*' AND tbl_name <> 'flyway_schema_history' AND sql IS NOT NULL
                ORDER BY type, name
                """)) {
            while (registros.next()) {
                catalogo.put(registros.getString("type") + "." + registros.getString("name"),
                        registros.getString("sql").replaceAll("\\s+", " ").strip());
            }
        }
        return catalogo;
    }

    private static void prepararCatalogo(String url, String usuario, String senha) {
        try {
            Flyway.configure().dataSource(url, usuario, senha)
                    .locations("classpath:db/postgresql")
                    .load().migrate();
        } catch (FlywayException erro) {
            throw new Falha("Falha ao preparar ou validar o catálogo Flyway PostgreSQL.", erro);
        }
    }

    private static Map<String, Long> transferir(Connection origem, Connection destino, ZoneId fuso)
            throws SQLException {
        destino.setAutoCommit(false);
        try (var sql = destino.createStatement()) {
            sql.execute("LOCK TABLE " + String.join(", ", VERIFICAR_DESTINO) + " IN SHARE ROW EXCLUSIVE MODE");
            for (String nome : VERIFICAR_DESTINO) {
                try (var registros = sql.executeQuery("SELECT 1 FROM " + nome + " LIMIT 1")) {
                    if (registros.next()) {
                        throw new Falha("O destino já contém registros em " + nome + ".");
                    }
                }
            }

            Map<String, Long> contagens = new LinkedHashMap<>();
            for (Tabela tabela : TABELAS) {
                contagens.put(tabela.nome(), copiarTabela(origem, destino, tabela, fuso));
                long proximo;
                try (var resultado = sql.executeQuery("SELECT max(id) FROM " + tabela.nome())) {
                    resultado.next();
                    proximo = Math.addExact(Math.max(0, resultado.getLong(1)), 1);
                }
                sql.execute("ALTER TABLE " + tabela.nome() + " ALTER COLUMN id RESTART WITH " + proximo);
            }
            destino.commit();
            return Collections.unmodifiableMap(contagens);
        } catch (SQLException | RuntimeException erro) {
            try {
                destino.rollback();
            } catch (SQLException rollback) {
                erro.addSuppressed(rollback);
            }
            throw erro;
        }
    }

    private static long copiarTabela(Connection origem, Connection destino, Tabela tabela, ZoneId fuso) {
        String select = "SELECT " + tabela.colunas() + " FROM " + tabela.nome() + " ORDER BY id";
        String parametros = String.join(", ", Collections.nCopies(tabela.colunas().split(",").length, "?"));
        String insert = "INSERT INTO " + tabela.nome() + " (" + tabela.colunas() + ") VALUES (" + parametros + ")";
        try (var consulta = origem.createStatement();
             var registros = consulta.executeQuery(select);
             var gravacao = destino.prepareStatement(insert)) {
            var meta = gravacao.getParameterMetaData();
            int[] tipos = new int[meta.getParameterCount()];
            for (int i = 0; i < tipos.length; i++) {
                tipos[i] = meta.getParameterType(i + 1);
            }

            long total = 0;
            while (registros.next()) {
                for (int i = 0; i < tipos.length; i++) {
                    atribuir(gravacao, registros, i + 1, tipos[i], fuso);
                }
                gravacao.executeUpdate();
                total++;
            }
            return total;
        } catch (SQLException erro) {
            throw erroSql("Falha na carga da tabela " + tabela.nome() + ".", erro);
        } catch (RuntimeException erro) {
            throw new Falha("Valor incompatível na tabela " + tabela.nome() + ".", erro);
        }
    }

    private static void atribuir(PreparedStatement destino, ResultSet origem, int indice, int tipo, ZoneId fuso)
            throws SQLException {
        Object valor = origem.getObject(indice);
        if (valor == null) {
            destino.setNull(indice, tipo);
            return;
        }

        switch (tipo) {
            case Types.BIGINT -> destino.setLong(indice, inteiro(valor));
            case Types.INTEGER -> destino.setInt(indice, Math.toIntExact(inteiro(valor)));
            case Types.BIT, Types.BOOLEAN -> {
                long numero = inteiro(valor);
                if (numero != 0 && numero != 1) {
                    throw new IllegalArgumentException("Booleano SQLite inválido.");
                }
                destino.setBoolean(indice, numero == 1);
            }
            case Types.DECIMAL, Types.NUMERIC -> destino.setBigDecimal(indice,
                    new BigDecimal(origem.getString(indice)));
            case Types.TIMESTAMP -> {
                LocalDateTime data = valor instanceof String texto
                        ? LocalDateTime.parse(texto.replace(' ', 'T'))
                        : LocalDateTime.ofInstant(Instant.ofEpochMilli(inteiro(valor)), fuso);
                destino.setObject(indice, data);
            }
            case Types.VARCHAR, Types.LONGVARCHAR -> destino.setString(indice, (String) valor);
            default -> throw new IllegalArgumentException("Tipo de destino incompatível.");
        }
    }

    private static long inteiro(Object valor) {
        if (valor instanceof Integer numero) {
            return numero.longValue();
        }
        if (valor instanceof Long numero) {
            return numero;
        }
        throw new IllegalArgumentException("Inteiro SQLite inválido.");
    }

    private static Falha erroSql(String mensagem, SQLException erro) {
        String estado = erro.getSQLState();
        return new Falha(mensagem + (estado == null ? "" : " SQLSTATE " + estado + "."), erro);
    }

    private record Tabela(String nome, String colunas) {}

    public static final class Falha extends RuntimeException {
        public Falha(String mensagem) {
            super(mensagem);
        }

        private Falha(String mensagem, Throwable causa) {
            super(mensagem, causa);
        }
    }
}
