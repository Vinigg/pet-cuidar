package com.petcuidar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ComandoMigracaoPostgresqlIT {

    @TempDir Path temporario;

    @Test
    void imprimeContagensSemExporDadosNaCargaOuNaFalha() throws Exception {
        Path origem = temporario.resolve("origem.sqlite");
        SqliteMigracaoTeste.criar(origem);
        var destino = PostgresqlTeste.novoBanco();
        var ambiente = Map.of(
                "SPRING_DATASOURCE_URL", destino.url(),
                "SPRING_DATASOURCE_USERNAME", destino.usuario(),
                "SPRING_DATASOURCE_PASSWORD", destino.senha());
        var saida = new ByteArrayOutputStream();
        var erros = new ByteArrayOutputStream();
        var stdout = new PrintStream(saida, true, StandardCharsets.UTF_8);
        var stderr = new PrintStream(erros, true, StandardCharsets.UTF_8);
        String[] args = {"migrar-sqlite", origem.toString(), "UTC"};

        assertEquals(0, PetCuidarApplication.migrarSqlite(args, ambiente, stdout, stderr));
        String resultado = saida.toString(StandardCharsets.UTF_8);
        assertTrue(resultado.contains("tutores: 1"));
        assertTrue(resultado.contains("historico_atendimentos: 1"));
        assertTrue(resultado.contains("Total importado: 10"));
        assertTrue(erros.toString(StandardCharsets.UTF_8).isEmpty());
        assertEquals(1, destino.jdbc().queryForObject("SELECT COUNT(*) FROM consultas", Integer.class));

        saida.reset();
        assertEquals(1, PetCuidarApplication.migrarSqlite(args, ambiente, stdout, stderr));
        assertTrue(erros.toString(StandardCharsets.UTF_8).contains("destino"));
        assertTrue(saida.toString(StandardCharsets.UTF_8).isEmpty());

        var urlComSenha = Map.of(
                "SPRING_DATASOURCE_URL", destino.url() + "&password=" + destino.senha(),
                "SPRING_DATASOURCE_USERNAME", destino.usuario(),
                "SPRING_DATASOURCE_PASSWORD", destino.senha());
        assertEquals(2, PetCuidarApplication.migrarSqlite(args, urlComSenha, stdout, stderr));

        String mensagens = resultado + saida.toString(StandardCharsets.UTF_8) + erros.toString(StandardCharsets.UTF_8);
        assertFalse(mensagens.contains(destino.senha()));
        assertFalse(mensagens.contains("tutor@example.test"));
        assertFalse(mensagens.contains("Descrição clínica teste"));
    }
}
