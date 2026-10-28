package com.petcuidar.controller;

import com.petcuidar.dto.PetOpcaoDTO;
import com.petcuidar.service.PetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PetController.class)
class PetControllerHttpTest {
    @Autowired MockMvc mvc;
    @MockBean PetService petService;

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
}