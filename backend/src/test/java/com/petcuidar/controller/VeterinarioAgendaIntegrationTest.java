package com.petcuidar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcuidar.repository.VeterinarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.flywaydb.core.Flyway;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VeterinarioAgendaIntegrationTest {
    private static final String DB = "jdbc:sqlite:target/agenda-" + UUID.randomUUID() + ".db";
    private static final LocalDate SEGUNDA = LocalDate.now().plusDays(30)
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    private static final String PERIODOS = """
            [{"diaSemana":"MONDAY","horaInicio":"08:00","horaFim":"12:00"},
             {"diaSemana":"MONDAY","horaInicio":"14:00","horaFim":"18:00"}]
            """;

    @DynamicPropertySource
        static void bancoIsolado(DynamicPropertyRegistry registry) throws SQLException {
                try (var connection = DriverManager.getConnection(DB); var statement = connection.createStatement()) {
                        statement.execute("CREATE TABLE veterinarios (id INTEGER PRIMARY KEY AUTOINCREMENT, nome VARCHAR(255), "
                                        + "especialidade VARCHAR(255), versao BIGINT NOT NULL DEFAULT 0)");
                        statement.execute("INSERT INTO veterinarios(id, nome, especialidade) VALUES(42, 'Veterinario legado', 'Geral')");
                }
        registry.add("spring.datasource.url", () -> DB);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired VeterinarioRepository veterinarios;
        @Autowired Flyway flyway;

    @Test
    void migraVeterinarioLegadoSemPerderDados() throws Exception {
                assertEquals(0, flyway.migrate().migrationsExecuted);
        mvc.perform(get("/veterinarios/42"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Veterinario legado"))
                .andExpect(jsonPath("$.ativo").value(true));
        mvc.perform(get("/veterinarios/42/horarios-atendimento"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7));
        mvc.perform(get("/veterinarios/42/disponibilidade").param("data", SEGUNDA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(20));
    }

    private long cadastrarPet() throws Exception {
        String resposta = mvc.perform(post("/tutores").contentType(APPLICATION_JSON).content("""
                {"nome":"Tutor Agenda","email":"%s@example.com","pets":[{"nome":"Rex","especie":"Cachorro","idade":2}]}
                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(resposta).get("pets").get(0).get("id").asLong();
    }

    private String consulta(long vetId, long petId, String dataHora) {
        return "{\"petId\":" + petId + ",\"veterinarioId\":" + vetId + ",\"dataHora\":\"" + dataHora + "\"}";
    }

    private long cadastrar(String periodos) throws Exception {
        String resposta = mvc.perform(post("/veterinarios").contentType(APPLICATION_JSON)
                        .content("{\"nome\":\"Dra. Ana\",\"especialidade\":\"Clinica geral\",\"horarios\":" + periodos + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.versao").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(resposta).get("id").asLong();
    }

    @Test
    void cadastraEscalaComAlmocoERespeitaDiaDeFolga() throws Exception {
        long id = cadastrar(PERIODOS);
        mvc.perform(get("/veterinarios/{id}", id)).andExpect(status().isOk());
        mvc.perform(get("/veterinarios/{id}/horarios-atendimento", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/veterinarios/{id}/disponibilidade", id).param("data", SEGUNDA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(16))
                .andExpect(jsonPath("$[7].horario").value(SEGUNDA + "T11:30:00"))
                .andExpect(jsonPath("$[8].horario").value(SEGUNDA + "T14:00:00"));
        mvc.perform(get("/veterinarios/{id}/disponibilidade", id).param("data", SEGUNDA.plusDays(1).toString()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void semEscalaOuDataPassadaNaoOfereceHorarios() throws Exception {
        long id = cadastrar("[]");
        mvc.perform(get("/veterinarios/{id}/disponibilidade", id).param("data", SEGUNDA.toString()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        id = cadastrar(PERIODOS);
        mvc.perform(get("/veterinarios/{id}/disponibilidade", id).param("data", LocalDate.now().minusDays(1).toString()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void escalaInvalidaNaoPersisteCadastroParcial() throws Exception {
        long antes = veterinarios.count();
        mvc.perform(post("/veterinarios").contentType(APPLICATION_JSON).content("""
                {"nome":"Teste","horarios":[
                  {"diaSemana":"MONDAY","horaInicio":"08:00","horaFim":"12:00"},
                  {"diaSemana":"MONDAY","horaInicio":"11:30","horaFim":"14:00"}]}
                """))
                .andExpect(status().isBadRequest());
        assertEquals(antes, veterinarios.count());
        mvc.perform(post("/veterinarios").contentType(APPLICATION_JSON).content("{\"nome\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void substituiEscalaEInativaSemExcluirHistorico() throws Exception {
        long id = cadastrar(PERIODOS);
        mvc.perform(put("/veterinarios/{id}/horarios-atendimento", id).contentType(APPLICATION_JSON)
                        .content("{\"horarios\":[]}"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(put("/veterinarios/{id}", id).contentType(APPLICATION_JSON)
                        .content("{\"nome\":\"Dra. Ana\",\"ativo\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
        mvc.perform(get("/veterinarios").param("ativo", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + id + ")]").isNotEmpty());
        mvc.perform(get("/veterinarios/{id}", id)).andExpect(status().isOk());
    }

    @Test
    void veterinarioInexistenteOuCorpoMalformadoRetornaErroPadrao() throws Exception {
        mvc.perform(get("/veterinarios/999999/horarios-atendimento"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        long id = cadastrar("[]");
        mvc.perform(put("/veterinarios/{id}/horarios-atendimento", id).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/veterinarios/{id}/horarios-atendimento", id).contentType(APPLICATION_JSON)
                        .content("{\"horarios\":[null]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void agendamentoEReagendamentoValidamEscalaEPreservamConsultaOriginal() throws Exception {
        long id = cadastrar(PERIODOS);
        long petId = cadastrarPet();
        for (String horario : new String[]{SEGUNDA + "T12:00:00", SEGUNDA + "T08:15:00",
                SEGUNDA.plusDays(1) + "T08:00:00"}) {
            mvc.perform(post("/consultas").contentType(APPLICATION_JSON).content(consulta(id, petId, horario)))
                    .andExpect(status().isBadRequest());
        }
        String corpo = consulta(id, petId, SEGUNDA + "T08:00:00");
        String resposta = mvc.perform(post("/consultas").contentType(APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long consultaId = mapper.readTree(resposta).get("id").asLong();
        mvc.perform(post("/consultas").contentType(APPLICATION_JSON).content(corpo))
                .andExpect(status().isConflict());
        mvc.perform(patch("/consultas/{id}/reagendamento", consultaId).contentType(APPLICATION_JSON)
                        .content("{\"dataHora\":\"" + SEGUNDA + "T12:30:00\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/consultas").param("data", SEGUNDA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + consultaId + ")].dataHora").value(SEGUNDA + "T08:00:00"));
        mvc.perform(put("/veterinarios/{id}/horarios-atendimento", id).contentType(APPLICATION_JSON)
                        .content("{\"horarios\":[]}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/veterinarios/{id}/horarios-atendimento", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(put("/veterinarios/{id}", id).contentType(APPLICATION_JSON)
                        .content("{\"nome\":\"Inativo\",\"ativo\":false}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/veterinarios/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(true));
        mvc.perform(post("/veterinarios/{id}/bloqueios", id).contentType(APPLICATION_JSON)
                        .content("{\"inicio\":\"" + SEGUNDA + "T08:15:00\",\"fim\":\"" + SEGUNDA + "T09:00:00\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void bloqueioParcialImpedeTodaConsultaQueSobrepoeOPeriodo() throws Exception {
        long id = cadastrar(PERIODOS);
        long petId = cadastrarPet();
        String bloqueio = "{\"inicio\":\"" + SEGUNDA + "T08:15:00\",\"fim\":\"" + SEGUNDA
                + "T08:45:00\",\"motivo\":\"Ausencia\"}";
        String resposta = mvc.perform(post("/veterinarios/{id}/bloqueios", id).contentType(APPLICATION_JSON).content(bloqueio))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long bloqueioId = mapper.readTree(resposta).get("id").asLong();
        mvc.perform(post("/veterinarios/{id}/bloqueios", id).contentType(APPLICATION_JSON).content(bloqueio))
                .andExpect(status().isConflict());
        mvc.perform(get("/veterinarios/{id}/disponibilidade", id).param("data", SEGUNDA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].disponivel").value(false))
                .andExpect(jsonPath("$[1].disponivel").value(false)).andExpect(jsonPath("$[2].disponivel").value(true));
        mvc.perform(post("/consultas").contentType(APPLICATION_JSON)
                        .content(consulta(id, petId, SEGUNDA + "T08:00:00")))
                .andExpect(status().isBadRequest());
        String agendada = mvc.perform(post("/consultas").contentType(APPLICATION_JSON)
                        .content(consulta(id, petId, SEGUNDA + "T09:00:00")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long consultaId = mapper.readTree(agendada).get("id").asLong();
        mvc.perform(patch("/consultas/{id}/reagendamento", consultaId).contentType(APPLICATION_JSON)
                        .content("{\"dataHora\":\"" + SEGUNDA + "T08:30:00\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/veterinarios/{id}/bloqueios", id)
                        .param("inicio", SEGUNDA.toString()).param("fim", SEGUNDA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        long outro = cadastrar("[]");
        mvc.perform(delete("/veterinarios/{id}/bloqueios/{bloqueioId}", outro, bloqueioId))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/veterinarios/{id}/bloqueios/{bloqueioId}", id, bloqueioId))
                .andExpect(status().isNoContent());
        mvc.perform(post("/consultas").contentType(APPLICATION_JSON)
                        .content(consulta(id, petId, SEGUNDA + "T08:00:00")))
                .andExpect(status().isCreated());
    }

    @Test
    void naoAgendaVeterinarioInativoMesmoComEscala() throws Exception {
        long id = cadastrar(PERIODOS);
        long petId = cadastrarPet();
        mvc.perform(put("/veterinarios/{id}", id).contentType(APPLICATION_JSON)
                        .content("{\"nome\":\"Dra. Ana\",\"ativo\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/veterinarios"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
        mvc.perform(get("/veterinarios/{id}/disponibilidade", id).param("data", SEGUNDA.toString()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(post("/consultas").contentType(APPLICATION_JSON)
                        .content(consulta(id, petId, SEGUNDA + "T08:00:00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelamentoPermiteRemoverEscalaEInativarSemApagarConsulta() throws Exception {
        long id = cadastrar(PERIODOS);
        long petId = cadastrarPet();
        String resposta = mvc.perform(post("/consultas").contentType(APPLICATION_JSON)
                        .content(consulta(id, petId, SEGUNDA + "T08:00:00")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long consultaId = mapper.readTree(resposta).get("id").asLong();
        mvc.perform(patch("/consultas/{id}/status", consultaId).contentType(APPLICATION_JSON)
                        .content("{\"status\":\"CANCELADA\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/veterinarios/{id}/horarios-atendimento", id).contentType(APPLICATION_JSON)
                        .content("{\"horarios\":[]}"))
                .andExpect(status().isOk());
        mvc.perform(put("/veterinarios/{id}", id).contentType(APPLICATION_JSON)
                        .content("{\"nome\":\"Dra. Ana\",\"ativo\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/consultas").param("data", SEGUNDA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + consultaId + ")].status").value("CANCELADA"));
    }

    @Test
    void rejeitaPeriodosInvalidosSemAlterarEscalaAtual() throws Exception {
        long id = cadastrar(PERIODOS);
        for (String periodo : new String[]{
                "{\"diaSemana\":\"MONDAY\",\"horaInicio\":\"08:15\",\"horaFim\":\"12:00\"}",
                "{\"diaSemana\":\"MONDAY\",\"horaInicio\":\"18:00\",\"horaFim\":\"08:00\"}",
                "{\"diaSemana\":\"MONDAY\",\"horaInicio\":\"08:00\",\"horaFim\":\"08:00\"}",
                "{\"diaSemana\":\"INVALIDO\",\"horaInicio\":\"08:00\",\"horaFim\":\"12:00\"}",
                "{\"diaSemana\":\"MONDAY\",\"horaInicio\":null,\"horaFim\":\"12:00\"}"}) {
            mvc.perform(put("/veterinarios/{id}/horarios-atendimento", id).contentType(APPLICATION_JSON)
                            .content("{\"horarios\":[" + periodo + "]}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/veterinarios/{id}/horarios-atendimento", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void bloqueioDeDiaInteiroNaoAlteraEscalaEValidaPeriodo() throws Exception {
        long id = cadastrar(PERIODOS);
        mvc.perform(post("/veterinarios/{id}/bloqueios", id).contentType(APPLICATION_JSON)
                        .content("{\"inicio\":\"" + SEGUNDA + "T00:00:00\",\"fim\":\""
                                + SEGUNDA.plusDays(1) + "T00:00:00\",\"motivo\":\"Feriado\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/veterinarios/{id}/disponibilidade", id).param("data", SEGUNDA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(16))
                .andExpect(jsonPath("$[?(@.disponivel == true)]").isEmpty());
        mvc.perform(get("/veterinarios/{id}/horarios-atendimento", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(post("/veterinarios/{id}/bloqueios", id).contentType(APPLICATION_JSON)
                        .content("{\"inicio\":\"" + SEGUNDA + "T10:00:00\",\"fim\":\"" + SEGUNDA + "T08:00:00\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/veterinarios/{id}/bloqueios", id)
                        .param("inicio", SEGUNDA.plusDays(1).toString()).param("fim", SEGUNDA.toString()))
                .andExpect(status().isBadRequest());
    }
}