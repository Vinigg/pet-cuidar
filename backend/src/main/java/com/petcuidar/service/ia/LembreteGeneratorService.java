package com.petcuidar.service.ia;

import com.petcuidar.config.IaProperties;
import com.petcuidar.model.Consulta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import jakarta.annotation.PreDestroy;

/**
 * Geracao do texto de lembrete via IA (US3.1).
 *
 * Padrao aplicado (Secao 3.4 / 4.2 do documento):
 *  1. Chama o modelo de linguagem com timeout curto.
 *  2. Guardrail: valida deterministicamente se o texto gerado contem o
 *     nome do pet, do tutor e a data/hora corretos.
 *  3. Se a chamada falhar, estourar o timeout, ou o guardrail rejeitar o
 *     texto, usa-se SEMPRE o template fixo de fallback - o lembrete nunca
 *     deixa de ser enviado por causa de um problema na IA.
 *  4. Um "circuit breaker" simples interrompe as chamadas ao modelo apos
 *     N falhas seguidas, evitando penalizar cada requisicao com o timeout
 *     inteiro enquanto o servico externo estiver fora do ar.
 */
@Service
public class LembreteGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(LembreteGeneratorService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm");

    private final IaProperties props;
    private final LembreteIaClient clienteIa;
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

    public LembreteGeneratorService(IaProperties props, LembreteIaClient clienteIa) {
        this.props = props;
        this.clienteIa = clienteIa;
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
        String nomeTutor = consulta.getPet().getTutor().getNome();
        String nomePet = consulta.getPet().getNome();
        String dataHoraFmt = consulta.getDataHora().format(FMT);
        String veterinario = consulta.getVeterinario().getNome();

        String fallback = montarTemplateFallback(nomeTutor, nomePet, dataHoraFmt, veterinario);

        if (!adquirirPermissao()) {
            fallbacks.increment();
            log.warn("Circuit breaker de IA aberto - usando fallback direto (fallbacks={})", fallbacks.sum());
            return new ResultadoLembrete(null, fallback, true);
        }

        String textoGerado;
        try {
            textoGerado = chamarModeloComTimeout(nomeTutor, nomePet, dataHoraFmt, veterinario);
        } catch (Exception e) {
            log.warn("Falha ao gerar lembrete via IA: {}", e.getClass().getSimpleName());
            registrarFalha();
            fallbacks.increment();
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.info("Fallback deterministico utilizado (falhas={}, timeouts={}, fallbacks={})", falhas.sum(), timeouts.sum(), fallbacks.sum());
            return new ResultadoLembrete(null, fallback, true);
        }

        if (!guardrailAprova(textoGerado, nomeTutor, nomePet, dataHoraFmt)) {
            log.warn("Guardrail rejeitou a resposta da IA - usando fallback");
            invalidas.increment();
            registrarFalha();
            fallbacks.increment();
            log.info("Fallback deterministico utilizado apos rejeicao do guardrail (respostas_invalidas={}, fallbacks={})", invalidas.sum(), fallbacks.sum());
            return new ResultadoLembrete(textoGerado, fallback, true);
        }

        registrarSucesso();

        return new ResultadoLembrete(textoGerado, textoGerado, false);
    }

    /**
     * Chamada ao cliente de IA com timeout. Neste projeto o bean padrão é o
     * simulador local, sem custo ou credenciais. Um adaptador de provedor pode
     * implementar LembreteIaClient sem mudar o circuit breaker ou o fallback.
     */
    private String chamarModeloComTimeout(String nomeTutor, String nomePet, String dataHoraFmt, String veterinario)
            throws TimeoutException, ExecutionException, InterruptedException {
        Future<String> future = executor.submit(() -> clienteIa.gerar(nomeTutor, nomePet, dataHoraFmt, veterinario));
        try { return future.get(props.getTimeoutMs(), TimeUnit.MILLISECONDS); }
        catch (TimeoutException e) { timeouts.increment(); future.cancel(true); throw e; }
    }

    private boolean guardrailAprova(String texto, String nomeTutor, String nomePet, String dataHoraFmt) {
        if (texto == null) return false;
        return texto.contains(nomeTutor) && texto.contains(nomePet) && texto.contains(dataHoraFmt);
    }

    private String montarTemplateFallback(String nomeTutor, String nomePet, String dataHoraFmt, String veterinario) {
        return String.format(
                "Ola %s, lembramos que %s tem consulta agendada com %s em %s. Contamos com voces!",
                nomeTutor, nomePet, veterinario, dataHoraFmt);
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
