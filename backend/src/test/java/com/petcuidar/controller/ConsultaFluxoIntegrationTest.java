package com.petcuidar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcuidar.model.*;
import com.petcuidar.repository.*;
import com.petcuidar.service.ConsultaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Fluxos PC-027 (status), PC-028 (cancelamento/reagendamento) e o bloqueio
 * de lembrete para consulta cancelada, ponta a ponta: HTTP -> service ->
 * maquina de estados -> SQLite isolado.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConsultaFluxoIntegrationTest {
    private static final String DB = "jdbc:sqlite:target/consultas-" + UUID.randomUUID() + ".db";
    private static final LocalDate DIA = LocalDate.now().plusDays(30);

    @DynamicPropertySource
    static void bancoIsolado(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ConsultaService consultaService;
    @Autowired ConsultaRepository consultas;
    @Autowired PetRepository pets;
    @Autowired TutorRepository tutores;
    @Autowired VeterinarioRepository veterinarios;
    @Autowired PredicaoNoShowRepository predicoes;
    @SpyBean LembreteRepository lembretes;

    private Pet pet;
    private Veterinario vet;

    @BeforeEach
    void preparar() {
        lembretes.deleteAll();
        predicoes.deleteAll();
        consultas.deleteAll();
        pets.deleteAll();
        tutores.deleteAll();

        Tutor tutor = new Tutor();
        tutor.setNome("Ana Souza");
        tutor.setEmail(UUID.randomUUID() + "@example.com");
        tutor = tutores.save(tutor);
        Pet novo = new Pet();
        novo.setNome("Rex");
        novo.setTutor(tutor);
        pet = pets.save(novo);
        vet = veterinarios.findAll().get(0);
    }

    private Consulta consulta(LocalDateTime dataHora) {
        Consulta c = new Consulta();
        c.setPet(pet);
        c.setVeterinario(vet);
        c.setDataHora(dataHora);
        return consultas.save(c);
    }

    private Consulta recarregar(Consulta c) { return consultas.findById(c.getId()).orElseThrow(); }

    private ResultActions patchStatus(Object id, String corpo) throws Exception {
        return mvc.perform(patch("/consultas/" + id + "/status").contentType(APPLICATION_JSON).content(corpo));
    }

    private ResultActions reagendar(Object id, String corpo) throws Exception {
        return mvc.perform(patch("/consultas/" + id + "/reagendamento").contentType(APPLICATION_JSON).content(corpo));
    }

    private static String corpoStatus(String s) { return "{\"status\":\"" + s + "\"}"; }

    private static String dataHora(LocalDateTime d) { return "{\"dataHora\":\"" + d + "\"}"; }

    // ---------------- PC-027: atualizacao de status ----------------

    @Test
    void confirmacaoValidaPersisteERetornaConsulta() throws Exception {
        Consulta c = consulta(DIA.atTime(10, 0));
        patchStatus(c.getId(), corpoStatus("CONFIRMADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(c.getId()))
                .andExpect(jsonPath("$.status").value("CONFIRMADA"))
                .andExpect(jsonPath("$.petNome").value("Rex"))
                .andExpect(jsonPath("$.tutorNome").value("Ana Souza"));
        assertEquals(StatusConsulta.CONFIRMADA, recarregar(c).getStatus());
    }

    @Test
    void statusDeConsultaInexistenteRetorna404() throws Exception {
        patchStatus(999_999, corpoStatus("CONFIRMADA"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void statusInvalidoOuAusenteRetorna400ESemAlteracao() throws Exception {
        Consulta c = consulta(DIA.atTime(10, 0));
        patchStatus(c.getId(), corpoStatus("DESCONHECIDO")).andExpect(status().isBadRequest());
        patchStatus(c.getId(), "{}").andExpect(status().isBadRequest());
        patchStatus(c.getId(), "{status:").andExpect(status().isBadRequest());
        patchStatus("abc", corpoStatus("CONFIRMADA")).andExpect(status().isBadRequest());
        assertEquals(StatusConsulta.AGENDADA, recarregar(c).getStatus());
    }

    @Test
    void transicaoNaoPermitidaRetorna409EPreservaStatus() throws Exception {
        Consulta c = consulta(DIA.atTime(10, 0));
        patchStatus(c.getId(), corpoStatus("REALIZADA")).andExpect(status().isOk());
        patchStatus(c.getId(), corpoStatus("CONFIRMADA"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensagem").exists());
        patchStatus(c.getId(), corpoStatus("AGENDADA")).andExpect(status().isConflict());
        assertEquals(StatusConsulta.REALIZADA, recarregar(c).getStatus());
    }

    // ---------------- PC-028: cancelamento ----------------

    @Test
    void cancelamentoValidoLiberaOHorario() throws Exception {
        LocalDateTime horario = DIA.atTime(11, 0);
        Consulta c = consulta(horario);
        patchStatus(c.getId(), corpoStatus("CONFIRMADA")).andExpect(status().isOk());
        patchStatus(c.getId(), corpoStatus("CANCELADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
        assertEquals(StatusConsulta.CANCELADA, recarregar(c).getStatus());

        String novoAgendamento = "{\"petId\":" + pet.getId() + ",\"veterinarioId\":" + vet.getId()
                + ",\"dataHora\":\"" + horario + "\"}";
        mvc.perform(post("/consultas").contentType(APPLICATION_JSON).content(novoAgendamento))
                .andExpect(status().isCreated());
    }

    @Test
    void cancelamentoDeConsultaInexistenteRetorna404() throws Exception {
        patchStatus(999_999, corpoStatus("CANCELADA")).andExpect(status().isNotFound());
    }

    @Test
    void cancelamentoEmEstadoOuMomentoNaoPermitidoRetorna409() throws Exception {
        Consulta realizada = consulta(DIA.atTime(10, 0));
        patchStatus(realizada.getId(), corpoStatus("REALIZADA")).andExpect(status().isOk());
        patchStatus(realizada.getId(), corpoStatus("CANCELADA")).andExpect(status().isConflict());
        assertEquals(StatusConsulta.REALIZADA, recarregar(realizada).getStatus());

        Consulta passada = consulta(LocalDate.now().minusDays(1).atTime(10, 0));
        patchStatus(passada.getId(), corpoStatus("CANCELADA")).andExpect(status().isConflict());
        assertEquals(StatusConsulta.AGENDADA, recarregar(passada).getStatus());
    }

    @Test
    void lembreteDeConsultaCanceladaRetorna409() throws Exception {
        Consulta ativa = consulta(DIA.atTime(9, 0));
        mvc.perform(post("/consultas/" + ativa.getId() + "/lembrete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENVIADO"))
                .andExpect(jsonPath("$.textoEnviado").value(org.hamcrest.Matchers.containsString("Rex")));

        Consulta cancelada = consulta(DIA.atTime(9, 30));
        patchStatus(cancelada.getId(), corpoStatus("CANCELADA")).andExpect(status().isOk());
        mvc.perform(post("/consultas/" + cancelada.getId() + "/lembrete"))
                .andExpect(status().isConflict());
        assertTrue(lembretes.findByConsultaId(cancelada.getId()).isEmpty());
    }

    // ---------------- PC-028: reagendamento ----------------

    @Test
    void reagendamentoValidoAtualizaHorarioEPreservaRelacionamentos() throws Exception {
        Consulta c = consulta(DIA.atTime(10, 0));
        patchStatus(c.getId(), corpoStatus("CONFIRMADA")).andExpect(status().isOk());
        LocalDateTime novo = DIA.plusDays(1).atTime(14, 30);

        reagendar(c.getId(), dataHora(novo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(c.getId()))
                .andExpect(jsonPath("$.dataHora").value(novo + ":00"))
                .andExpect(jsonPath("$.status").value("CONFIRMADA"))
                .andExpect(jsonPath("$.petNome").value("Rex"))
                .andExpect(jsonPath("$.veterinarioNome").value(vet.getNome()));

        Consulta salva = recarregar(c);
        assertEquals(novo, salva.getDataHora());
        assertEquals(pet.getId(), salva.getPet().getId());
        assertEquals(vet.getId(), salva.getVeterinario().getId());
        assertEquals(1, consultas.count(), "reagendar nao cria nova consulta");

        var livres = consultaService.listarDisponibilidade(vet.getId(), DIA);
        assertTrue(livres.stream().anyMatch(h -> h.getHorario().equals(DIA.atTime(10, 0)) && h.isDisponivel()));
    }

    @Test
    void reagendamentoParaHorarioOcupadoRetorna409EPreservaOriginal() throws Exception {
        Consulta c = consulta(DIA.atTime(10, 0));
        LocalDateTime ocupado = DIA.atTime(15, 0);
        consulta(ocupado);

        reagendar(c.getId(), dataHora(ocupado))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        assertEquals(DIA.atTime(10, 0), recarregar(c).getDataHora());
    }

    @Test
    void reagendamentoParaHorarioDeConsultaCanceladaEhPermitido() throws Exception {
        Consulta c = consulta(DIA.atTime(10, 0));
        Consulta cancelada = consulta(DIA.atTime(16, 0));
        patchStatus(cancelada.getId(), corpoStatus("CANCELADA")).andExpect(status().isOk());
        reagendar(c.getId(), dataHora(DIA.atTime(16, 0))).andExpect(status().isOk());
    }

    @Test
    void reagendamentoComDataInvalidaRetorna400EPreservaOriginal() throws Exception {
        LocalDateTime original = DIA.atTime(10, 0);
        Consulta c = consulta(original);
        reagendar(c.getId(), dataHora(DIA.atTime(10, 15))).andExpect(status().isBadRequest());
        reagendar(c.getId(), dataHora(DIA.atTime(19, 0))).andExpect(status().isBadRequest());
        reagendar(c.getId(), dataHora(LocalDate.now().minusDays(2).atTime(10, 0))).andExpect(status().isBadRequest());
        reagendar(c.getId(), dataHora(original)).andExpect(status().isBadRequest());
        reagendar(c.getId(), "{}").andExpect(status().isBadRequest());
        reagendar(c.getId(), "{\"dataHora\":\"amanha\"}").andExpect(status().isBadRequest());
        assertEquals(original, recarregar(c).getDataHora());
    }

    @Test
    void reagendamentoDeConsultaInexistenteRetorna404() throws Exception {
        reagendar(999_999, dataHora(DIA.atTime(10, 0))).andExpect(status().isNotFound());
    }

    @Test
    void reagendamentoEmEstadoFinalOuAposOHorarioRetorna409() throws Exception {
        Consulta cancelada = consulta(DIA.atTime(10, 0));
        patchStatus(cancelada.getId(), corpoStatus("CANCELADA")).andExpect(status().isOk());
        reagendar(cancelada.getId(), dataHora(DIA.atTime(11, 0))).andExpect(status().isConflict());
        assertEquals(DIA.atTime(10, 0), recarregar(cancelada).getDataHora());

        Consulta passada = consulta(LocalDate.now().minusDays(1).atTime(10, 0));
        reagendar(passada.getId(), dataHora(DIA.atTime(12, 0))).andExpect(status().isConflict());
    }

    @Test
    void reagendamentoReabreLembreteParaONovoHorario() throws Exception {
        Consulta c = consulta(DIA.atTime(10, 0));
        mvc.perform(post("/consultas/" + c.getId() + "/lembrete")).andExpect(status().isOk());

        LocalDateTime novo = DIA.plusDays(2).atTime(8, 30);
        reagendar(c.getId(), dataHora(novo)).andExpect(status().isOk());
        Lembrete reaberto = lembretes.findByConsultaId(c.getId()).orElseThrow();
        assertEquals(StatusEnvioLembrete.PENDENTE, reaberto.getStatusEnvio());
        assertNull(reaberto.getTextoValidado());
        assertNull(reaberto.getDataEnvio());

        String dataNova = novo.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        mvc.perform(post("/consultas/" + c.getId() + "/lembrete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.textoEnviado").value(org.hamcrest.Matchers.containsString(dataNova + " às 08:30")));
    }

    @Test
    void falhaDepoisDaAlteracaoFazRollbackCompleto() {
        LocalDateTime original = DIA.atTime(10, 0);
        Consulta c = consulta(original);
        doThrow(new IllegalStateException("falha simulada")).when(lembretes).findByConsultaId(eq(c.getId()));

        assertThrows(IllegalStateException.class,
                () -> consultaService.reagendar(c.getId(), DIA.atTime(LocalTime.of(13, 0))));
        assertEquals(original, recarregar(c).getDataHora());
    }
}
