package com.petcuidar.controller;

import com.petcuidar.auth.Perfil;
import com.petcuidar.auth.SessaoTeste;
import org.springframework.jdbc.core.JdbcTemplate;
import com.petcuidar.model.*;
import com.petcuidar.repository.*;
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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Registro de atendimento ponta a ponta (HTTP -> service -> maquina de
 * estados -> SQLite isolado), incluindo o rollback: se o historico nao for
 * gravado, o status da consulta tambem nao muda.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AtendimentoFluxoIntegrationTest {
    private static final String DB = "jdbc:sqlite:target/atendimentos-" + UUID.randomUUID() + ".db";
    private static final LocalDateTime DATA_HORA = LocalDate.now().plusDays(10).atTime(10, 0);

    @DynamicPropertySource
    static void bancoIsolado(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate banco;
    @Autowired ConsultaRepository consultas;
    @Autowired PetRepository pets;
    @Autowired TutorRepository tutores;
    @Autowired VeterinarioRepository veterinarios;
    @SpyBean HistoricoAtendimentoRepository historicos;

    private Pet pet;
    private Veterinario vet;

    @BeforeEach
    void preparar() {
        reset(historicos);
        historicos.deleteAll();
        consultas.deleteAll();
        pets.deleteAll();
        tutores.deleteAll();

        Tutor tutor = new Tutor();
        tutor.setNome("Ana Souza");
        tutor.setEmail(UUID.randomUUID() + "@example.com");
        tutor = tutores.save(tutor);
        pet = novoPet("Rex", tutor);
        Veterinario profissional = new Veterinario();
        profissional.setNome("Veterinaria Fluxo");
        vet = veterinarios.save(profissional);
    }

    @Test
    void registroMarcaConsultaComoRealizada() throws Exception {
        Consulta c = consulta(pet);
        registrar(pet.getId(), c.getId()).andExpect(status().isCreated());

        assertEquals(StatusConsulta.REALIZADA, recarregar(c).getStatus());
        assertEquals(1, historicos.count());
    }

    @Test
    void registroAdicionalEmConsultaRealizadaNaoFalha() throws Exception {
        Consulta c = consulta(pet);
        registrar(pet.getId(), c.getId()).andExpect(status().isCreated());
        registrar(pet.getId(), c.getId()).andExpect(status().isCreated());

        assertEquals(StatusConsulta.REALIZADA, recarregar(c).getStatus());
        assertEquals(2, historicos.count());
    }

    @Test
    void consultaCanceladaResponde409ENaoGravaNada() throws Exception {
        Consulta c = consulta(pet);
        mvc.perform(patch("/consultas/" + c.getId() + "/status").with(SessaoTeste.assumir(banco, Perfil.COLABORADOR)).contentType(APPLICATION_JSON)
                .content("{\"status\":\"CANCELADA\"}")).andExpect(status().isOk());

        registrar(pet.getId(), c.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensagem").value(
                        "Nao e possivel registrar atendimento para consulta com status CANCELADA."));

        assertEquals(StatusConsulta.CANCELADA, recarregar(c).getStatus());
        assertEquals(0, historicos.count());
    }

    @Test
    void consultaDeOutroPetResponde400() throws Exception {
        Pet outro = novoPet("Luna", pet.getTutor());
        Consulta c = consulta(outro);

        registrar(pet.getId(), c.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertEquals(StatusConsulta.AGENDADA, recarregar(c).getStatus());
        assertEquals(0, historicos.count());
    }

    @Test
    void falhaAoGravarHistoricoDesfazATransicaoDeStatus() throws Exception {
        Consulta c = consulta(pet);
        doThrow(new IllegalStateException("falha simulada no banco")).when(historicos).save(any());

        registrar(pet.getId(), c.getId())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensagem").value(
                        "Erro interno ao processar a requisicao. Tente novamente mais tarde."));

        assertEquals(StatusConsulta.AGENDADA, recarregar(c).getStatus());
        reset(historicos);
        assertEquals(0, historicos.count());
    }

    @Test
    void atendimentoRetroativoUsaDataHoraDaConsulta() throws Exception {
        LocalDateTime horario = LocalDate.now().minusDays(1).atTime(9, 45, 15);
        Consulta c = consulta(pet, horario);

        registrar(pet.getId(), c.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataAtendimento").value(horario.toString()));

        assertEquals(horario, historicos.findAll().get(0).getData());
        System.out.println("PASS C6 prontuário usa data e hora da consulta");
    }

    @Test
    void atendimentoRetroativoMantemInstanteDaAuditoria() throws Exception {
        Consulta c = consulta(pet, LocalDate.now().minusDays(1).atTime(10, 30));
        LocalDateTime antesDaGravacao = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        registrar(pet.getId(), c.getId()).andExpect(status().isCreated());

        LocalDateTime depoisDaGravacao = LocalDateTime.now();
        HistoricoAtendimento atendimento = historicos.findAll().get(0);
        assertFalse(atendimento.getDataCriacao().isBefore(antesDaGravacao));
        assertFalse(atendimento.getDataAtualizacao().isBefore(atendimento.getDataCriacao()));
        assertFalse(atendimento.getDataAtualizacao().isAfter(depoisDaGravacao));
        System.out.println("PASS C7 auditoria usa o horário real da gravação");
    }

    @Test
    void nenhumRegistroParaConsultaCanceladaOuComNaoComparecimento() throws Exception {
        Consulta cancelada = consulta(pet);
        Consulta faltou = consulta(pet, LocalDate.now().minusDays(1).atTime(13, 30));

        encerrarConsulta(cancelada, StatusConsulta.CANCELADA);
        encerrarConsulta(faltou, StatusConsulta.NAO_COMPARECEU);

        registrar(pet.getId(), cancelada.getId()).andExpect(status().isConflict());
        registrar(pet.getId(), faltou.getId()).andExpect(status().isConflict());

        assertEquals(StatusConsulta.CANCELADA, recarregar(cancelada).getStatus());
        assertEquals(StatusConsulta.NAO_COMPARECEU, recarregar(faltou).getStatus());
        assertEquals(0, historicos.count());
        System.out.println("PASS C8 cancelada e não compareceu impedem o registro");
    }

    private void encerrarConsulta(Consulta consulta, StatusConsulta status) throws Exception {
        mvc.perform(patch("/consultas/" + consulta.getId() + "/status")
                .with(SessaoTeste.assumir(banco, Perfil.COLABORADOR))
                .contentType(APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions registrar(Long petId, Long consultaId) throws Exception {
        String corpo = "{\"petId\":" + petId + ",\"consultaId\":" + consultaId + ",\"veterinarioId\":" + vet.getId()
                + ",\"descricaoClinica\":\"Otite leve, tratamento topico prescrito.\"}";
        return mvc.perform(post("/atendimentos").with(SessaoTeste.assumir(banco, Perfil.VETERINARIO, vet.getId())).contentType(APPLICATION_JSON).content(corpo));
    }

    private Pet novoPet(String nome, Tutor tutor) {
        Pet novo = new Pet();
        novo.setNome(nome);
        novo.setTutor(tutor);
        return pets.save(novo);
    }

    private Consulta consulta(Pet dono) {
        return consulta(dono, DATA_HORA);
    }

    private Consulta consulta(Pet dono, LocalDateTime horario) {
        Consulta c = new Consulta();
        c.setPet(dono);
        c.setVeterinario(vet);
        c.setDataHora(horario);
        return consultas.save(c);
    }

    private Consulta recarregar(Consulta c) {
        return consultas.findById(c.getId()).orElseThrow();
    }
}
