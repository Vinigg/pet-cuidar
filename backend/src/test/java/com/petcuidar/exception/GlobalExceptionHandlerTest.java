package com.petcuidar.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.server.ResponseStatusException;

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

    @Test void integracaoIndisponivelRetorna502NoFormatoApiErro() {
        var response = handler.handleIntegracaoIndisponivel(
                new IntegracaoExternaIndisponivelException("Provedor de e-mail indisponivel"));
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals(502, response.getBody().getStatus());
        assertEquals("Provedor de e-mail indisponivel", response.getBody().getMensagem());
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test void integracaoTimeoutRetorna504NoFormatoApiErro() {
        var response = handler.handleIntegracaoTimeout(
                new IntegracaoExternaTimeoutException("Provedor de e-mail sem resposta"));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        assertEquals(504, response.getBody().getStatus());
        assertEquals("Provedor de e-mail sem resposta", response.getBody().getMensagem());
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test void erroInesperadoRetorna500ComMensagemGenericaSemDetalhes() {
        var response = handler.handleInesperado(
                new IllegalStateException("NullPointer em ana@example.com CPF 123.456.789-00"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().getStatus());
        assertEquals(GlobalExceptionHandler.MENSAGEM_ERRO_INTERNO, response.getBody().getMensagem());
        assertFalse(response.getBody().getMensagem().contains("ana@example.com"));
        assertFalse(response.getBody().getMensagem().contains("NullPointer"));
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test void excecaoDoFrameworkComStatusProprioNaoViraErro500() {
        var response = handler.handleInesperado(new HttpRequestMethodNotSupportedException("DELETE"));
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals(405, response.getBody().getStatus());
    }

    @Test void responseStatusException5xxViraMensagemGenerica() {
        var response = handler.handleInesperado(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "detalhe interno"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(GlobalExceptionHandler.MENSAGEM_ERRO_INTERNO, response.getBody().getMensagem());
    }

    @Test void argumentoInvalidoRetorna400() {
        var response = handler.handleIllegalArgument(new IllegalArgumentException("Parametro invalido"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getStatus());
    }
}
