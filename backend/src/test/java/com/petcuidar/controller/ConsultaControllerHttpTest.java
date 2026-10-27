package com.petcuidar.controller;

import com.petcuidar.exception.GlobalExceptionHandler;
import com.petcuidar.exception.RecursoNaoEncontradoException;
import com.petcuidar.exception.TransicaoEstadoInvalidaException;
import com.petcuidar.service.ConsultaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConsultaController.class)
@Import(GlobalExceptionHandler.class)
class ConsultaControllerHttpTest {
    @Autowired MockMvc mvc;
    @MockBean ConsultaService consultaService;

    @Test void consultaInexistenteResponde404NoFormatoPadrao() throws Exception {
        when(consultaService.atualizarStatus(eq(404L), any()))
                .thenThrow(new RecursoNaoEncontradoException("Consulta nao encontrada"));
        mvc.perform(patch("/consultas/404/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMADA\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensagem").value("Consulta nao encontrada"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test void transicaoInvalidaResponde409NoFormatoPadrao() throws Exception {
        when(consultaService.atualizarStatus(eq(1L), any()))
                .thenThrow(new TransicaoEstadoInvalidaException("Transicao invalida"));
        mvc.perform(patch("/consultas/1/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REALIZADA\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensagem").value("Transicao invalida"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test void corpoInvalidoResponde400() throws Exception {
        mvc.perform(patch("/consultas/1/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DESCONHECIDO\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(consultaService);
    }
}
