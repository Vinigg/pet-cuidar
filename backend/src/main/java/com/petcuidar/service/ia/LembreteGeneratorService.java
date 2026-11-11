package com.petcuidar.service.ia;

import com.petcuidar.config.IaProperties;
import com.petcuidar.model.Consulta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import jakarta.annotation.PreDestroy;

/**
 * Geracao do texto de lembrete via IA (US3.1).
 *
 * Padrao aplicado (Secao 3.4 / 4.2 do documento):
 *  1. Chama o modelo de linguagem com timeout curto.
 *  2. Guardrail ({@link LembreteGuardrail}): valida deterministicamente nome
 *     do tutor, do pet, data e horario, e rejeita dados nao fornecidos.
 *  3. Se a chamada falhar, estourar o timeout, responder sem texto util, ou o
 *     guardrail rejeitar o texto, usa-se SEMPRE o template ({@link LembreteTemplate})
 *     - o lembrete nunca deixa de ser enviado por causa de um problema na IA.
 *  4. Um {@link CircuitBreakerIa} proprio desta integracao interrompe as
 *     chamadas ao modelo apos N falhas seguidas, evitando penalizar cada
 *     requisicao com o timeout inteiro enquanto o servico externo estiver
 *     fora do ar. Timeout e limites: ia.lembrete.* (padrao: ia.timeout-ms e
 *     ia.circuit-breaker.*).
 */
@Service
public class LembreteGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(LembreteGeneratorService.class);

    private final long timeoutMs;
    private final CircuitBreakerIa circuitBreaker;
    private final LembreteIaClient clienteIa;
    private final LembreteTemplate template;
    private final LembreteGuardrail guardrail;
    private final ExecutorService executor = new ThreadPoolExecutor(0, 4, 60, TimeUnit.SECONDS,
            new SynchronousQueue<>(), r -> {
                Thread t = new Thread(r, "ia-lembrete"); t.setDaemon(true); return t;
            }, new ThreadPoolExecutor.AbortPolicy());

    // Mantido por compatibilidade: espelha CircuitBreakerIa.Estado.
    public enum EstadoCircuito { FECHADO, ABERTO, TESTE }
    private final LongAdder timeouts = new LongAdder(), fallbacks = new LongAdder(), invalidas = new LongAdder();

    public record Metricas(EstadoCircuito estado, int falhasConsecutivas, long falhas, long timeouts,
                           long fallbacks, long respostasInvalidas, long sucessos) {}

    @Autowired
    public LembreteGeneratorService(IaProperties props, LembreteIaClient clienteIa,
                                    LembreteTemplate template, LembreteGuardrail guardrail) {
        this(props, clienteIa, template, guardrail, Clock.systemUTC());
    }

    LembreteGeneratorService(IaProperties props, LembreteIaClient clienteIa,
                             LembreteTemplate template, LembreteGuardrail guardrail, Clock clock) {
        this.timeoutMs = props.timeoutMs(props.getLembrete());
        this.circuitBreaker = new CircuitBreakerIa("ia-lembrete", props.circuitBreaker(props.getLembrete()), clock);
        this.clienteIa = clienteIa;
        this.template = template;
        this.guardrail = guardrail;
    }

    /** Conveniencia para testes: template e guardrail com o nome padrao da clinica. */
    public LembreteGeneratorService(IaProperties props, LembreteIaClient clienteIa) {
        this(props, clienteIa, new LembreteTemplate("Pet Cuidar"), new LembreteGuardrail("Pet Cuidar"));
    }

    public static class ResultadoLembrete {
        public final String textoGeradoIa; // pode ser null se a chamada falhou
        public final String textoValidado; // o que de fato sera enviado
        public final boolean usouFallback;

        public ResultadoLembrete(String textoGeradoIa, String textoValidado, boolean usouFallback) {
            this.textoGeradoIa = textoGeradoIa;
            this.textoValidado = textoValidado;
            this.usouFallback = usouFallback;
        }
    }

    public ResultadoLembrete gerar(Consulta consulta) {
        DadosLembrete dados = DadosLembrete.de(consulta);
        // Valida os campos obrigatorios antes de chamar a IA: sem eles nem o
        // template consegue produzir um lembrete correto.
        String fallback = template.montar(dados);

        if (!circuitBreaker.permitirChamada()) {
            fallbacks.increment();
            log.warn("Circuit breaker de IA aberto - usando fallback direto (fallbacks={})", fallbacks.sum());
            return new ResultadoLembrete(null, fallback, true);
        }

        String textoGerado;
        try {
            textoGerado = chamarModeloComTimeout(dados);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            Throwable causa = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
            if (causa instanceof RespostaIaInvalidaException) {
                invalidas.increment();
                log.warn("Resposta da IA sem texto utilizavel ({})", causa.getMessage());
            } else {
                log.warn("Falha ao gerar lembrete via IA: {}", causa.getClass().getSimpleName());
            }
            circuitBreaker.registrarFalha();
            fallbacks.increment();
            log.info("Fallback deterministico utilizado (falhas={}, timeouts={}, respostas_invalidas={}, fallbacks={})",
                    circuitBreaker.obterMetricas().falhas(), timeouts.sum(), invalidas.sum(), fallbacks.sum());
            return new ResultadoLembrete(null, fallback, true);
        }

        Optional<LembreteGuardrail.Motivo> rejeicao = guardrail.avaliar(textoGerado, dados);
        if (rejeicao.isPresent()) {
            log.warn("Guardrail rejeitou a resposta da IA (motivo={}) - usando fallback", rejeicao.get());
            invalidas.increment();
            circuitBreaker.registrarFalha();
            fallbacks.increment();
            log.info("Fallback deterministico utilizado apos rejeicao do guardrail (respostas_invalidas={}, fallbacks={})", invalidas.sum(), fallbacks.sum());
            return new ResultadoLembrete(textoGerado, fallback, true);
        }

        circuitBreaker.registrarSucesso();

        String aprovado = textoGerado.strip();
        return new ResultadoLembrete(textoGerado, aprovado, false);
    }

    /**
     * Chamada ao cliente de IA com timeout (ia.lembrete.timeout-ms). O cliente
     * ativo e definido por ia.provider (simulador, openai ou llm-externo);
     * circuit breaker, guardrail e fallback independem do provedor.
     */
    private String chamarModeloComTimeout(DadosLembrete dados)
            throws TimeoutException, ExecutionException, InterruptedException {
        Future<String> future = executor.submit(() -> clienteIa.gerar(dados));
        try { return future.get(timeoutMs, TimeUnit.MILLISECONDS); }
        catch (TimeoutException e) { timeouts.increment(); future.cancel(true); throw e; }
    }

    public Metricas obterMetricas() {
        CircuitBreakerIa.Metricas cb = circuitBreaker.obterMetricas();
        return new Metricas(EstadoCircuito.valueOf(cb.estado().name()), cb.falhasConsecutivas(), cb.falhas(),
                timeouts.sum(), fallbacks.sum(), invalidas.sum(), cb.sucessos());
    }

    @PreDestroy public void encerrarExecutor() { executor.shutdownNow(); }
}
