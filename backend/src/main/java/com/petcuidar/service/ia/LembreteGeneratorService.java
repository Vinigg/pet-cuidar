package com.petcuidar.service.ia;

import com.petcuidar.config.IaProperties;
import com.petcuidar.model.Consulta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;

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
 *  4. Um "circuit breaker" simples interrompe as chamadas ao modelo apos
 *     N falhas seguidas, evitando penalizar cada requisicao com o timeout
 *     inteiro enquanto o servico externo estiver fora do ar.
 */
@Service
public class LembreteGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(LembreteGeneratorService.class);

    private final IaProperties props;
    private final LembreteIaClient clienteIa;
    private final LembreteTemplate template;
    private final LembreteGuardrail guardrail;
    private final ExecutorService executor = new ThreadPoolExecutor(0, 4, 60, TimeUnit.SECONDS,
            new SynchronousQueue<>(), r -> {
                Thread t = new Thread(r, "ia-lembrete"); t.setDaemon(true); return t;
            }, new ThreadPoolExecutor.AbortPolicy());

    // Estado simples do circuit breaker (em memoria; para producao, mover para Redis
    // ou usar uma lib dedicada como resilience4j em ambiente multi-instancia)
    public enum EstadoCircuito { FECHADO, ABERTO, TESTE }
    private EstadoCircuito estadoCircuito = EstadoCircuito.FECHADO;
    private int falhasSeguidas = 0;
    private long circuitoAbertoAte = 0;
    private final LongAdder falhas = new LongAdder(), timeouts = new LongAdder(), fallbacks = new LongAdder(), invalidas = new LongAdder(), sucessos = new LongAdder();

    public record Metricas(EstadoCircuito estado, int falhasConsecutivas, long falhas, long timeouts,
                           long fallbacks, long respostasInvalidas, long sucessos) {}

    @Autowired
    public LembreteGeneratorService(IaProperties props, LembreteIaClient clienteIa,
                                    LembreteTemplate template, LembreteGuardrail guardrail) {
        this.props = props;
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

        if (!adquirirPermissao()) {
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
            registrarFalha();
            fallbacks.increment();
            log.info("Fallback deterministico utilizado (falhas={}, timeouts={}, respostas_invalidas={}, fallbacks={})",
                    falhas.sum(), timeouts.sum(), invalidas.sum(), fallbacks.sum());
            return new ResultadoLembrete(null, fallback, true);
        }

        Optional<LembreteGuardrail.Motivo> rejeicao = guardrail.avaliar(textoGerado, dados);
        if (rejeicao.isPresent()) {
            log.warn("Guardrail rejeitou a resposta da IA (motivo={}) - usando fallback", rejeicao.get());
            invalidas.increment();
            registrarFalha();
            fallbacks.increment();
            log.info("Fallback deterministico utilizado apos rejeicao do guardrail (respostas_invalidas={}, fallbacks={})", invalidas.sum(), fallbacks.sum());
            return new ResultadoLembrete(textoGerado, fallback, true);
        }

        registrarSucesso();

        String aprovado = textoGerado.strip();
        return new ResultadoLembrete(textoGerado, aprovado, false);
    }

    /**
     * Chamada ao cliente de IA com timeout (ia.timeout-ms). O cliente ativo e
     * definido por ia.llm.provider (simulador ou anthropic); circuit breaker,
     * guardrail e fallback independem do provedor.
     */
    private String chamarModeloComTimeout(DadosLembrete dados)
            throws TimeoutException, ExecutionException, InterruptedException {
        Future<String> future = executor.submit(() -> clienteIa.gerar(dados));
        try { return future.get(props.getTimeoutMs(), TimeUnit.MILLISECONDS); }
        catch (TimeoutException e) { timeouts.increment(); future.cancel(true); throw e; }
    }

    private synchronized boolean adquirirPermissao() {
        long agora = System.currentTimeMillis();
        if (estadoCircuito == EstadoCircuito.ABERTO) {
            if (agora < circuitoAbertoAte) return false;
            estadoCircuito = EstadoCircuito.TESTE;
            log.info("Circuit breaker de IA em tentativa de recuperacao");
            return true;
        }
        return estadoCircuito != EstadoCircuito.TESTE;
    }

    private synchronized void registrarFalha() {
        falhasSeguidas++;
        falhas.increment();
        if (falhasSeguidas >= props.getCircuitBreaker().getMaxFalhas()) {
            circuitoAbertoAte = System.currentTimeMillis() + props.getCircuitBreaker().getResetMs();
            boolean reaberto = estadoCircuito != EstadoCircuito.ABERTO;
            estadoCircuito = EstadoCircuito.ABERTO;
            if (reaberto) log.warn("Circuit breaker de IA ABERTO por {} ms apos {} falhas seguidas",
                    props.getCircuitBreaker().getResetMs(), falhasSeguidas);
        }
    }

    private synchronized void registrarSucesso() {
        if (estadoCircuito != EstadoCircuito.FECHADO) log.info("Circuit breaker de IA FECHADO apos recuperacao");
        estadoCircuito = EstadoCircuito.FECHADO;
        falhasSeguidas = 0;
        sucessos.increment();
    }

    public synchronized Metricas obterMetricas() {
        return new Metricas(estadoCircuito, falhasSeguidas, falhas.sum(), timeouts.sum(),
                fallbacks.sum(), invalidas.sum(), sucessos.sum());
    }

    @PreDestroy public void encerrarExecutor() { executor.shutdownNow(); }
}
