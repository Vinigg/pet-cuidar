package com.petcuidar.service.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcuidar.config.IaProperties;
import com.petcuidar.model.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercita o SDK real (serializacao, cabecalhos, leitura da resposta) contra
 * um servidor HTTP local que imita a API - nenhuma chamada sai da maquina.
 */
class AnthropicLembreteIaClientTest {

    private static final DadosLembrete DADOS = new DadosLembrete("Ana", "Rex", "Dra. Bia",
            LocalDateTime.of(2026, 10, 28, 15, 30));
    private static final String TEXTO_OK =
            "Olá, Ana! Lembramos que Rex tem consulta com Dra. Bia em 28/10/2026 às 15:30. Até lá!";

    record Requisicao(String caminho, String autorizacao, String versao, String beta, String corpo) {}
    record Resposta(int status, String corpo, long atrasoMs) {}

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Requisicao> recebidas = new CopyOnWriteArrayList<>();
    private final AtomicReference<Resposta> proxima = new AtomicReference<>();
    private HttpServer servidor;

    @BeforeEach
    void iniciarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", this::responder);
        servidor.start();
    }

    @AfterEach
    void pararServidor() { servidor.stop(0); }

    private void responder(HttpExchange troca) throws IOException {
        var h = troca.getRequestHeaders();
        recebidas.add(new Requisicao(troca.getRequestURI().getPath(), h.getFirst("Authorization"),
                h.getFirst("anthropic-version"), h.getFirst("anthropic-beta"),
                new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        Resposta r = proxima.get();
        try {
            if (r.atrasoMs() > 0) Thread.sleep(r.atrasoMs());
            byte[] corpo = r.corpo().getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(r.status(), corpo.length);
            troca.getResponseBody().write(corpo);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException ignorada) {
            // cliente desistiu (timeout)
        } finally {
            troca.close();
        }
    }

    private static String mensagem(String texto, String stopReason) {
        String content = texto == null ? "[]"
                : "[{\"type\":\"text\",\"text\":" + jsonString(texto) + ",\"citations\":null}]";
        return "{\"id\":\"msg_01\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-opus-5-5\","
                + "\"content\":" + content + ",\"stop_reason\":\"" + stopReason + "\",\"stop_sequence\":null,"
                + "\"usage\":{\"input_tokens\":120,\"output_tokens\":40}}";
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private IaProperties props(long timeoutMs, String apiKey) {
        IaProperties p = new IaProperties();
        p.setTimeoutMs(timeoutMs);
        p.getCircuitBreaker().setMaxFalhas(2);
        p.getCircuitBreaker().setResetMs(60_000);
        p.getLlm().setProvider("anthropic");
        p.getLlm().setApiKey(apiKey);
        p.getLlm().setBaseUrl("http://127.0.0.1:" + servidor.getAddress().getPort());
        return p;
    }

    private AnthropicLembreteIaClient cliente(IaProperties p) {
        return new AnthropicLembreteIaClient(AnthropicLembreteIaClient.criarCliente(p), p.getLlm(), "Pet Cuidar");
    }

    private Consulta consulta() {
        Tutor tutor = new Tutor(); tutor.setNome("Ana"); tutor.setEmail("ana@example.com"); tutor.setTelefone("85999990000");
        Pet pet = new Pet(); pet.setNome("Rex"); pet.setTutor(tutor);
        Veterinario vet = new Veterinario(); vet.setNome("Dra. Bia");
        Consulta c = new Consulta(); c.setPet(pet); c.setVeterinario(vet);
        c.setDataHora(LocalDateTime.of(2026, 10, 28, 15, 30));
        return c;
    }

    @Test void respostaValidaEnviaRequisicaoCorretaERetornaTexto() throws Exception {
        proxima.set(new Resposta(200, mensagem(TEXTO_OK, "end_turn"), 0));
        AnthropicLembreteIaClient cliente = cliente(props(2000, "chave-de-teste"));
        try {
            assertEquals(TEXTO_OK, cliente.gerar(DADOS));
        } finally { cliente.encerrar(); }

        assertEquals(1, recebidas.size());
        Requisicao req = recebidas.get(0);
        assertEquals("/v1/messages", req.caminho());
        assertEquals("Bearer chave-de-teste", req.autorizacao());
        assertNotNull(req.versao());
        assertTrue(req.beta().contains(AnthropicLembreteIaClient.BETA_FALLBACK_SERVIDOR));

        JsonNode corpo = mapper.readTree(req.corpo());
        assertEquals("claude-opus-5-5", corpo.get("model").asText());
        assertEquals("default", corpo.get("fallbacks").asText());
        assertEquals("low", corpo.get("output_config").get("effort").asText());
        assertTrue(corpo.get("system").asText().contains("português do Brasil"));
        String usuario = corpo.get("messages").get(0).get("content").asText();
        assertTrue(usuario.contains("Tutor: Ana") && usuario.contains("Pet: Rex")
                && usuario.contains("Data: 28/10/2026") && usuario.contains("Horário: 15:30"));
    }

    @Test void respostaVaziaRecusaOuTruncadaEhInvalida() {
        AnthropicLembreteIaClient cliente = cliente(props(2000, "k"));
        try {
            proxima.set(new Resposta(200, mensagem(null, "end_turn"), 0));
            assertThrows(RespostaIaInvalidaException.class, () -> cliente.gerar(DADOS));
            proxima.set(new Resposta(200, mensagem("   ", "end_turn"), 0));
            assertThrows(RespostaIaInvalidaException.class, () -> cliente.gerar(DADOS));
            proxima.set(new Resposta(200, mensagem("Não posso ajudar.", "refusal"), 0));
            assertThrows(RespostaIaInvalidaException.class, () -> cliente.gerar(DADOS));
            proxima.set(new Resposta(200, mensagem("Olá, Ana! Lembr", "max_tokens"), 0));
            assertThrows(RespostaIaInvalidaException.class, () -> cliente.gerar(DADOS));
        } finally { cliente.encerrar(); }
    }

    @Test void respostaMalformadaOuErroHttpLancaExcecaoSemRetentativa() {
        AnthropicLembreteIaClient cliente = cliente(props(2000, "k"));
        try {
            proxima.set(new Resposta(200, "isto nao e json", 0));
            assertThrows(RuntimeException.class, () -> cliente.gerar(DADOS));
            recebidas.clear();
            proxima.set(new Resposta(500, "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"x\"}}", 0));
            assertThrows(RuntimeException.class, () -> cliente.gerar(DADOS));
            assertEquals(1, recebidas.size(), "maxRetries=0: o circuit breaker decide sobre novas tentativas");
        } finally { cliente.encerrar(); }
    }

    @Test void semChaveNaoChamaProvedor() {
        AnthropicLembreteIaClient cliente = cliente(props(2000, ""));
        assertThrows(IllegalStateException.class, () -> cliente.gerar(DADOS));
        assertTrue(recebidas.isEmpty());
    }

    // ---- integracao com gerador (timeout + circuit breaker + guardrail + fallback) ----

    @Test void geradorUsaTextoDaIaQuandoValido() {
        proxima.set(new Resposta(200, mensagem(TEXTO_OK, "end_turn"), 0));
        IaProperties p = props(2000, "k");
        AnthropicLembreteIaClient cliente = cliente(p);
        LembreteGeneratorService gerador = new LembreteGeneratorService(p, cliente);
        try {
            var r = gerador.gerar(consulta());
            assertFalse(r.usouFallback);
            assertEquals(TEXTO_OK, r.textoValidado);
            assertFalse(recebidas.get(0).corpo().contains("ana@example.com"));
            assertFalse(recebidas.get(0).corpo().contains("85999990000"));
        } finally { gerador.encerrarExecutor(); cliente.encerrar(); }
    }

    @Test void geradorRejeitaRespostaInconsistenteEUsaTemplate() {
        String inconsistente = "Olá, Ana! Rex tem consulta em 28/10/2026 às 16:00.";
        proxima.set(new Resposta(200, mensagem(inconsistente, "end_turn"), 0));
        IaProperties p = props(2000, "k");
        AnthropicLembreteIaClient cliente = cliente(p);
        LembreteGeneratorService gerador = new LembreteGeneratorService(p, cliente);
        try {
            var r = gerador.gerar(consulta());
            assertTrue(r.usouFallback);
            assertEquals(inconsistente, r.textoGeradoIa, "saida bruta preservada para auditoria");
            assertTrue(r.textoValidado.contains("28/10/2026 às 15:30"));
            assertFalse(r.textoValidado.contains("16:00"));
            assertEquals(1, gerador.obterMetricas().respostasInvalidas());
        } finally { gerador.encerrarExecutor(); cliente.encerrar(); }
    }

    @Test void geradorUsaTemplateEmTimeoutDoProvedor() {
        proxima.set(new Resposta(200, mensagem(TEXTO_OK, "end_turn"), 1500));
        IaProperties p = props(200, "k");
        AnthropicLembreteIaClient cliente = cliente(p);
        LembreteGeneratorService gerador = new LembreteGeneratorService(p, cliente);
        try {
            long inicio = System.nanoTime();
            var r = gerador.gerar(consulta());
            long decorridoMs = (System.nanoTime() - inicio) / 1_000_000;
            assertTrue(r.usouFallback);
            assertNull(r.textoGeradoIa);
            assertTrue(decorridoMs < 1500, "nao espera a resposta lenta: " + decorridoMs + "ms");
            assertEquals(1, gerador.obterMetricas().falhas());
        } finally { gerador.encerrarExecutor(); cliente.encerrar(); }
    }

    @Test void geradorAbreCircuitoAposFalhasENaoChamaMaisOProvedor() {
        proxima.set(new Resposta(503, "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"x\"}}", 0));
        IaProperties p = props(2000, "k");
        AnthropicLembreteIaClient cliente = cliente(p);
        LembreteGeneratorService gerador = new LembreteGeneratorService(p, cliente);
        try {
            assertTrue(gerador.gerar(consulta()).usouFallback);
            assertTrue(gerador.gerar(consulta()).usouFallback);
            assertEquals(LembreteGeneratorService.EstadoCircuito.ABERTO, gerador.obterMetricas().estado());
            int chamadas = recebidas.size();
            var r = gerador.gerar(consulta());
            assertTrue(r.usouFallback);
            assertTrue(r.textoValidado.startsWith("Olá, Ana!"));
            assertEquals(chamadas, recebidas.size(), "circuito aberto nao chama o provedor");
        } finally { gerador.encerrarExecutor(); cliente.encerrar(); }
    }

    @Test void geradorSemChaveResolveComTemplate() {
        IaProperties p = props(2000, "");
        AnthropicLembreteIaClient cliente = cliente(p);
        LembreteGeneratorService gerador = new LembreteGeneratorService(p, cliente);
        try {
            var r = gerador.gerar(consulta());
            assertTrue(r.usouFallback);
            assertTrue(recebidas.isEmpty());
        } finally { gerador.encerrarExecutor(); }
    }
}
