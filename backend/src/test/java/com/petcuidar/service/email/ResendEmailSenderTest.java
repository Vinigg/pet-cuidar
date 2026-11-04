package com.petcuidar.service.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcuidar.config.EmailProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ResendEmailSenderTest {
    private HttpServer server;

    @AfterEach
    void encerrar() {
        if (server != null) server.stop(0);
    }

    @Test
    void enviaTextoValidadoComChaveDeIdempotencia() throws Exception {
        AtomicReference<String> chave = new AtomicReference<>(), corpo = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/emails", exchange -> {
            chave.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            corpo.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        server.start();

        ResendEmailSender sender = sender();
        sender.enviar("ana@example.com", "Consulta", "Texto validado", "lembrete-consulta-10");

        assertEquals("lembrete-consulta-10", chave.get());
        assertTrue(corpo.get().contains("ana@example.com"));
        assertTrue(corpo.get().contains("Texto validado"));
    }

    @Test
    void classificaRateLimitComoFalhaTransitoria() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/emails", exchange -> {
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        server.start();

        EmailDeliveryException erro = assertThrows(EmailDeliveryException.class,
                () -> sender().enviar("ana@example.com", "Consulta", "Texto", "chave"));

        assertTrue(erro.isTransitoria());
        assertEquals("Provedor de e-mail respondeu HTTP 429", erro.getMessage());
    }

    private ResendEmailSender sender() {
        EmailProperties properties = new EmailProperties();
        properties.setFrom("Pet Cuidar <no-reply@example.com>");
        properties.getResend().setApiKey("chave-de-teste");
        properties.getResend().setBaseUrl("http://localhost:" + server.getAddress().getPort());
        return new ResendEmailSender(properties, new ObjectMapper());
    }
}
