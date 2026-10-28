package com.petcuidar.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test void transicaoInvalidaRetorna409NoFormatoApiErro() {
        var response = handler.handleTransicao(new TransicaoEstadoInvalidaException("Transicao invalida"));
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().getStatus());
        assertEquals("Transicao invalida", response.getBody().getMensagem());
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test void argumentoInvalidoRetorna400() {
        var response = handler.handleIllegalArgument(new IllegalArgumentException("Parametro invalido"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getStatus());
    }
}
