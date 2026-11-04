package com.petcuidar.controller;

import com.petcuidar.dto.AtualizacaoAtendimentoRequestDTO;
import com.petcuidar.dto.AtendimentoResponseDTO;
import com.petcuidar.dto.RegistroAtendimentoRequestDTO;
import com.petcuidar.service.AtendimentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AtendimentoController.class)
@DisplayName("Testes do Controller de Atendimento")
class AtendimentoControllerHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AtendimentoService atendimentoService;

    @Autowired
    private ObjectMapper objectMapper;

    private AtendimentoResponseDTO atendimentoResponse;
    private RegistroAtendimentoRequestDTO registroRequest;
    private AtualizacaoAtendimentoRequestDTO atualizacaoRequest;

    @BeforeEach
    void setUp() {
        atendimentoResponse = new AtendimentoResponseDTO();
        atendimentoResponse.setId(1L);
        atendimentoResponse.setPetId(1L);
        atendimentoResponse.setPetNome("Rex");
        atendimentoResponse.setVeterinarioId(1L);
        atendimentoResponse.setVeterinarioNome("Dr. João");
        atendimentoResponse.setDescricaoClinica("Pet apresenta sintomas de otite");
        atendimentoResponse.setDataCriacao(LocalDateTime.now());
        atendimentoResponse.setDataAtualizacao(LocalDateTime.now());

        registroRequest = new RegistroAtendimentoRequestDTO();
        registroRequest.setPetId(1L);
        registroRequest.setConsultaId(1L);
        registroRequest.setVeterinarioId(1L);
        registroRequest.setDescricaoClinica("Pet apresenta sintomas de otite");

        atualizacaoRequest = new AtualizacaoAtendimentoRequestDTO();
        atualizacaoRequest.setAtendimentoId(1L);
        atualizacaoRequest.setVeterinarioId(1L);
        atualizacaoRequest.setDescricaoClinica("Pet apresenta sintomas de otite atualizado");
    }

    @Test
    @DisplayName("POST /atendimentos - Deve registrar novo atendimento com sucesso")
    void testRegistrarAtendimentoComSucesso() throws Exception {
        when(atendimentoService.registrarAtendimento(any())).thenReturn(atendimentoResponse);

        mockMvc.perform(post("/atendimentos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registroRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.petNome").value("Rex"))
                .andExpect(jsonPath("$.veterinarioNome").value("Dr. João"));
    }

    @Test
    @DisplayName("POST /atendimentos - Deve retornar erro de validação com descrição vazia")
    void testRegistrarAtendimentoDescricaoVazia() throws Exception {
        RegistroAtendimentoRequestDTO requestVazio = new RegistroAtendimentoRequestDTO();
        requestVazio.setPetId(1L);
        requestVazio.setConsultaId(1L);
        requestVazio.setVeterinarioId(1L);
        requestVazio.setDescricaoClinica(""); // Vazio

        mockMvc.perform(post("/atendimentos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestVazio)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /atendimentos - Deve retornar erro de validação com petId nulo")
    void testRegistrarAtendimentoPetIdNulo() throws Exception {
        RegistroAtendimentoRequestDTO requestInvalido = new RegistroAtendimentoRequestDTO();
        requestInvalido.setPetId(null); // Nulo
        requestInvalido.setConsultaId(1L);
        requestInvalido.setVeterinarioId(1L);
        requestInvalido.setDescricaoClinica("Descrição válida");

        mockMvc.perform(post("/atendimentos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /atendimentos - Deve atualizar atendimento com sucesso")
    void testAtualizarAtendimentoComSucesso() throws Exception {
        when(atendimentoService.atualizarAtendimento(any())).thenReturn(atendimentoResponse);

        mockMvc.perform(patch("/atendimentos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(atualizacaoRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.descricaoClinica").exists());
    }

    @Test
    @DisplayName("PATCH /atendimentos - Deve retornar erro de validação")
    void testAtualizarAtendimentoComDadosInvalidos() throws Exception {
        AtualizacaoAtendimentoRequestDTO requestInvalido = new AtualizacaoAtendimentoRequestDTO();
        requestInvalido.setAtendimentoId(1L);
        requestInvalido.setVeterinarioId(1L);
        requestInvalido.setDescricaoClinica(""); // Vazio

        mockMvc.perform(patch("/atendimentos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /atendimentos/{id} - Deve obter atendimento com sucesso")
    void testObterAtendimentoComSucesso() throws Exception {
        when(atendimentoService.obterAtendimento(1L)).thenReturn(atendimentoResponse);

        mockMvc.perform(get("/atendimentos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.petNome").value("Rex"));
    }

    @Test
    @DisplayName("GET /atendimentos/pet/{petId} - Deve listar atendimentos de um pet")
    void testListarAtendimentosPorPetComSucesso() throws Exception {
        AtendimentoResponseDTO atendimento2 = new AtendimentoResponseDTO();
        atendimento2.setId(2L);
        atendimento2.setPetId(1L);
        atendimento2.setPetNome("Rex");

        List<AtendimentoResponseDTO> atendimentos = Arrays.asList(atendimentoResponse, atendimento2);
        when(atendimentoService.listarAtendimentosPorPet(1L)).thenReturn(atendimentos);

        mockMvc.perform(get("/atendimentos/pet/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[1].id").value(2L))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /atendimentos/pet/{petId} - Deve retornar lista vazia quando pet não tem atendimentos")
    void testListarAtendimentosPorPetVazio() throws Exception {
        when(atendimentoService.listarAtendimentosPorPet(999L)).thenReturn(Arrays.asList());

        mockMvc.perform(get("/atendimentos/pet/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
