package com.petcuidar.controller;

import com.petcuidar.dto.AtendimentoResponseDTO;
import com.petcuidar.dto.ConsultaHistoricoDTO;
import com.petcuidar.dto.HistoricoPetDTO;
import com.petcuidar.dto.PetOpcaoDTO;
import com.petcuidar.exception.RecursoNaoEncontradoException;
import com.petcuidar.model.StatusConsulta;
import com.petcuidar.service.HistoricoAtendimentoService;
import com.petcuidar.service.PetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PetController.class)
class PetControllerHttpTest {
    @Autowired MockMvc mvc;
    @MockBean PetService petService;
    @MockBean HistoricoAtendimentoService historicoService;

    @Test void listaOpcoesSemExporDadosSensiveis() throws Exception {
        when(petService.listarOpcoes(null)).thenReturn(List.of(new PetOpcaoDTO(1L, "Luna", "Gato", "Ana")));
        mvc.perform(get("/pets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nome").value("Luna"))
                .andExpect(jsonPath("$[0].tutorNome").value("Ana"))
                .andExpect(jsonPath("$[0].cpf").doesNotExist())
                .andExpect(jsonPath("$[0].tutor").doesNotExist())
                .andExpect(jsonPath("$[0].alergiasCriticas").doesNotExist());
    }

    @Test void encaminhaFiltroPorTutor() throws Exception {
        when(petService.listarOpcoes(7L)).thenReturn(List.of());
        mvc.perform(get("/pets").param("tutorId", "7"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(petService).listarOpcoes(7L);
    }

    @Test void historicoRetornaConsultasSemSerializarEntidades() throws Exception {
        AtendimentoResponseDTO atendimento = new AtendimentoResponseDTO();
        atendimento.setId(100L);
        atendimento.setConsultaId(10L);
        atendimento.setDescricaoClinica("Retorno sem alteracoes");
        ConsultaHistoricoDTO consulta = new ConsultaHistoricoDTO(10L, LocalDateTime.of(2026, 11, 4, 17, 30),
                StatusConsulta.AGENDADA, 3L, "Alberto Siqueira", List.of(atendimento));
        when(historicoService.obterHistorico(2L)).thenReturn(new HistoricoPetDTO(
                2L, "Bob", "Pitbull", 4, "Joao", "Alergia a dipirona", List.of(consulta), List.of()));

        mvc.perform(get("/pets/2/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.petNome").value("Bob"))
                .andExpect(jsonPath("$.itensCriticos").value("Alergia a dipirona"))
                .andExpect(jsonPath("$.consultas[0].consultaId").value(10))
                .andExpect(jsonPath("$.consultas[0].veterinarioNome").value("Alberto Siqueira"))
                .andExpect(jsonPath("$.consultas[0].atendimentos[0].descricaoClinica").value("Retorno sem alteracoes"))
                .andExpect(jsonPath("$.tutor").doesNotExist())
                .andExpect(jsonPath("$.consultas[0].pet").doesNotExist());
    }

    @Test void historicoDePetInexistenteRetorna404() throws Exception {
        when(historicoService.obterHistorico(99L)).thenThrow(new RecursoNaoEncontradoException("Pet nao encontrado: id 99"));
        mvc.perform(get("/pets/99/historico"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.consultas").doesNotExist());
    }
}