package com.petcuidar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcuidar.service.email.EmailSender;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles({"prod", "postgres"})
@SpringBootTest(properties = "app.auth.admin-email=")
@AutoConfigureMockMvc
class ApiPostgresqlIT {

    private static PostgresqlTeste.Banco banco;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockBean EmailSender emailSender;

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry props) throws SQLException {
        banco = PostgresqlTeste.novoBanco();
        props.add("spring.datasource.url", banco::url);
        props.add("spring.datasource.username", banco::usuario);
        props.add("spring.datasource.password", banco::senha);
    }

    @Test
    void iniciaComCatalogoValidado() throws Exception {
        assertEquals("1", jdbc.queryForObject("SELECT version FROM flyway_schema_history", String.class));
        mvc.perform(get("/auth/sessao")).andExpect(status().isUnauthorized());
        assertEquals(banco.schema(), jdbc.queryForObject("SELECT current_schema()", String.class));

    }

    @Test
    void aplicaConexaoTlsComCertificadoEHostname() {
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()", Boolean.class));
        String semCertificado = banco.url().replaceFirst("sslrootcert=[^&]+",
                "sslrootcert=ca-inexistente-" + UUID.randomUUID() + ".crt");
        assertThrows(SQLException.class, () -> DriverManager.getConnection(
                semCertificado, banco.usuario(), banco.senha()));
        assertTrue(banco.url().contains("sslmode=verify-full"));
        var nomeIncorreto = banco.url().replace("127.0.0.1:", "localhost:");
        var falha = assertThrows(SQLException.class, () -> DriverManager.getConnection(
                nomeIncorreto, banco.usuario(), banco.senha()));
        assertTrue(falha.getMessage().contains("localhost"));
    }

    @Test
    void usuarioAutenticadoConsultaAgendaEGravaProntuario() throws Exception {
        long vet = id("INSERT INTO veterinarios(nome) VALUES ('Dra. Teste') RETURNING id");
        long outro = id("INSERT INTO veterinarios(nome) VALUES ('Outro profissional') RETURNING id");
        long tutor = id("INSERT INTO tutores(nome, email) VALUES ('Tutor teste', ?) RETURNING id", email());
        long pet = id("INSERT INTO pets(nome, tutor_id) VALUES ('Paciente teste', ?) RETURNING id", tutor);
        LocalDateTime horario = LocalDateTime.of(2026, 11, 23, 10, 30, 15);
        long consulta = id("""
                INSERT INTO consultas(pet_id, veterinario_id, data_hora) VALUES (?, ?, ?) RETURNING id
                """, pet, vet, horario);
        id("INSERT INTO consultas(pet_id, veterinario_id, data_hora) VALUES (?, ?, ?) RETURNING id",
                pet, outro, horario.plusHours(1));
        String conta = email();
        jdbc.update("""
                INSERT INTO contas(email, nome, perfil, veterinario_id) VALUES (?, 'Dra. Teste', 'VETERINARIO', ?)
                """, conta, vet);
        Cookie sessao = login(conta);

        mvc.perform(get("/consultas").param("data", horario.toLocalDate().toString()).cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(consulta))
                .andExpect(jsonPath("$[0].dataHora").value(horario.toString()));

        var criado = mvc.perform(post("/atendimentos").cookie(sessao).header("X-Pet-Cuidar", "1")
                        .contentType(APPLICATION_JSON).content("""
                                {"petId":%d,"consultaId":%d,"veterinarioId":%d,"descricaoClinica":"Registro do atendimento teste."}
                                """.formatted(pet, consulta, vet)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.consultaId").value(consulta))
                .andReturn().getResponse().getContentAsString();
        long atendimento = json.readTree(criado).get("id").asLong();

        mvc.perform(patch("/atendimentos").cookie(sessao).header("X-Pet-Cuidar", "1")
                        .contentType(APPLICATION_JSON).content("""
                                {"atendimentoId":%d,"veterinarioId":%d,"descricaoClinica":"Prontuário atualizado pelo teste."}
                                """.formatted(atendimento, vet)))
                .andExpect(status().isOk());

        assertEquals("REALIZADA", jdbc.queryForObject("SELECT status FROM consultas WHERE id = ?", String.class, consulta));
        assertEquals("Prontuário atualizado pelo teste.", jdbc.queryForObject(
                "SELECT descricao FROM historico_atendimentos WHERE id = ?", String.class, atendimento));
        System.out.println("PASS C3 PostgreSQL preserva login agenda e prontuario");
    }

    @Test
    void usuarioSemSessaoNaoLeDadosDoPet() throws Exception {
        mvc.perform(get("/pets")).andExpect(status().isUnauthorized());
    }

    private Cookie login(String email) throws Exception {
        var mensagens = new LinkedBlockingQueue<String>();
        doAnswer(chamada -> {
            mensagens.add(chamada.getArgument(2));
            return null;
        }).when(emailSender).enviar(anyString(), anyString(), anyString(), anyString());

        mvc.perform(post("/auth/codigo").header("X-Pet-Cuidar", "1").contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","finalidade":"LOGIN"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());

        String mensagem = mensagens.poll(5, TimeUnit.SECONDS);
        assertNotNull(mensagem);
        var codigo = Pattern.compile("[0-9]{8}").matcher(mensagem);
        assertTrue(codigo.find());
        var cookie = mvc.perform(post("/auth/verificar").header("X-Pet-Cuidar", "1").contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","finalidade":"LOGIN","codigo":"%s"}
                                """.formatted(email, codigo.group())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.perfil").value("VETERINARIO"))
                .andReturn().getResponse().getCookie("pc_sessao");
        assertNotNull(cookie);
        return cookie;
    }

    private long id(String sql, Object... argumentos) {
        return jdbc.queryForObject(sql, Long.class, argumentos);
    }

    private String email() {
        return UUID.randomUUID() + "@example.test";
    }
}
