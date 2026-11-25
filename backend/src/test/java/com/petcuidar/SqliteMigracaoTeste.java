package com.petcuidar;

import org.flywaydb.core.Flyway;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;

final class SqliteMigracaoTeste {

    static final long INSTANTE = 1789983000123L;

    private SqliteMigracaoTeste() {
    }

    static void criar(Path arquivo) throws SQLException {
        String url = "jdbc:sqlite:" + arquivo.toAbsolutePath();
        Flyway.configure().dataSource(url, null, null).load().migrate();
        try (var banco = DriverManager.getConnection(url); var sql = banco.createStatement()) {
            sql.executeUpdate("""
                    INSERT INTO tutores(id,nome,email,telefone,cpf,email_normalizado)
                    VALUES(11,'Tutor teste','tutor@example.test','000000000','00000000000','tutor@example.test')
                    """);
            sql.executeUpdate("""
                    INSERT INTO veterinarios(id,nome,especialidade,versao,ativo)
                    VALUES(21,'Dra. Teste','Clínica',3,1)
                    """);
            sql.executeUpdate("""
                    INSERT INTO pets(id,tutor_id,nome,especie,idade,alergias_criticas)
                    VALUES(31,11,'Paciente teste','Canino',3,'Observação clínica')
                    """);
            sql.executeUpdate("""
                    INSERT INTO horarios_atendimento_veterinario(id,veterinario_id,dia_semana,hora_inicio,hora_fim)
                    VALUES(41,21,'MONDAY','08:00','12:00')
                    """);
            sql.executeUpdate("""
                    INSERT INTO bloqueios_agenda_veterinario(id,veterinario_id,inicio,fim,motivo)
                    VALUES(51,21,%d,%d,'Bloqueio teste')
                    """.formatted(INSTANTE, INSTANTE + 1800000));
            sql.executeUpdate("""
                    INSERT INTO consultas(id,pet_id,veterinario_id,data_hora,status)
                    VALUES(61,31,21,%d,'REALIZADA')
                    """.formatted(INSTANTE));
            sql.executeUpdate("""
                    INSERT INTO historico_atendimentos(id,pet_id,consulta_id,descricao,resumo_ia,data,veterinario_id,
                        data_criacao,data_atualizacao,veterinario_criacao_id,veterinario_atualizacao_id,versao)
                    VALUES(71,31,61,'Descrição clínica teste','Resumo teste',%d,21,
                        '2026-09-22 11:00:00',%d,21,21,2)
                    """.formatted(INSTANTE, INSTANTE + 3600000));
            sql.executeUpdate("""
                    INSERT INTO lembretes(id,consulta_id,canal,texto_gerado_ia,texto_validado,status_envio,
                        data_envio,tentativas,ultimo_erro,data_ultima_tentativa,usou_fallback)
                    VALUES(81,61,'EMAIL','Lembrete inicial','Lembrete final','ENVIADO',%d,2,NULL,%d,0)
                    """.formatted(INSTANTE, INSTANTE));
            sql.executeUpdate("""
                    INSERT INTO predicoes_no_show(id,consulta_id,score_cru,score_usado,usou_fallback,motivo,data_calculo)
                    VALUES(91,61,0.372,0.400,1,'Predição teste',%d)
                    """.formatted(INSTANTE));
            sql.executeUpdate("""
                    INSERT INTO contas(id,email,nome,perfil,tutor_id,veterinario_id)
                    VALUES(101,'vet@example.test','Dra. Teste','VETERINARIO',NULL,21)
                    """);
            sql.executeUpdate("""
                    INSERT INTO desafios_email(email,finalidade,digest,criado_em,expira_em)
                    VALUES('vet@example.test','LOGIN','desafio-teste',1000,3000)
                    """);
            sql.executeUpdate("""
                    INSERT INTO credenciais_auth(digest,conta_id,expira_em) VALUES('sessao-teste',101,3000)
                    """);
        }
    }
}
