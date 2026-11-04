package com.petcuidar.service.ia;

import com.petcuidar.config.IaProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiIaClientTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "Dra. Bia")
    void enviaPromptComDadosPersistidosELeRespostaDoProvedor(String veterinario) throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                    {"choices":[{"message":{"content":"Ola Ana, consulta de Rex em 28/10/2026 as 15:30."}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            IaProperties props = new IaProperties();
            props.setBaseUrl("http://localhost:" + server.getAddress().getPort());
            props.setApiKey("chave-ficticia-de-teste");
            props.setModel("modelo-de-teste");
            props.setTimeoutMs(500);
            LembreteIaClient client = new OpenAiIaClient(props);

            String resposta = client.gerar(new DadosLembrete(
                "Ana", "Rex", veterinario, LocalDateTime.of(2026, 10, 28, 15, 30)));

            assertEquals("Ola Ana, consulta de Rex em 28/10/2026 as 15:30.", resposta);
            assertTrue(requestBody.get().contains("Ana"));
            assertTrue(requestBody.get().contains("Rex"));
            assertTrue(requestBody.get().contains("28/10/2026 as 15:30"));
            if (veterinario != null) {
                assertTrue(requestBody.get().contains("veterinario=" + veterinario));
            } else {
                assertFalse(requestBody.get().contains("veterinario="));
            }
            assertTrue(requestBody.get().contains("modelo-de-teste"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resumoEnviaHistoricoEItensCriticos() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                    {"choices":[{"message":{"content":"Rex recebeu a vacina anual."}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            IaProperties props = new IaProperties();
            props.setBaseUrl("http://localhost:" + server.getAddress().getPort());
            props.setApiKey("chave-ficticia-de-teste");
            props.setTimeoutMs(500);
            OpenAiIaClient client = new OpenAiIaClient(props);

            client.resumir("Rex", List.of("Vacina anual"), "Alergia a dipirona");

            assertTrue(requestBody.get().contains("Vacina anual"));
            assertTrue(requestBody.get().contains("Alergia a dipirona"));
        } finally {
            server.stop(0);
        }
    }
}
