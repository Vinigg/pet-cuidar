package com.petcuidar.service.ia;

import com.petcuidar.dto.ResumoHistoricoDTO;
import com.petcuidar.model.HistoricoAtendimento;
import com.petcuidar.model.Pet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jakarta.annotation.PreDestroy;
import com.petcuidar.config.IaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resumo do historico do pet para o veterinario (US5.1).
 *
 * Criterio de aceite de IA mais importante desta funcionalidade: itens
 * marcados como criticos (pet.alergiasCriticas) NUNCA podem depender do
 * texto livre gerado pela IA - eles sao sempre concatenados por regra fixa
 * ao final do resumo, garantindo que uma omissao do modelo nunca esconda
 * uma alergia do veterinario. O resumo tambem sempre acompanha um link
 * para o historico completo.
 *
 * Resiliencia: timeout (ia.resumo.timeout-ms) e um {@link CircuitBreakerIa}
 * proprio (ia.resumo.circuit-breaker.*). Com o circuito aberto, o resumo
 * deterministico e usado sem chamar o provedor. Falha de IA nunca vira erro
 * para o usuario: a resposta sai com usouFallback=true.
 */
@Service
public class ResumoHistoricoService {

    private static final Logger log = LoggerFactory.getLogger(ResumoHistoricoService.class);
    private final ResumoIaClient clienteIa;
    private final long timeoutMs;
    private final CircuitBreakerIa circuitBreaker;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "ia-resumo");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    public ResumoHistoricoService(ResumoIaClient clienteIa, IaProperties props) {
        this(clienteIa, props, Clock.systemUTC());
    }

    ResumoHistoricoService(ResumoIaClient clienteIa, IaProperties props, Clock clock) {
        this.clienteIa = clienteIa;
        this.timeoutMs = props.timeoutMs(props.getResumo());
        this.circuitBreaker = new CircuitBreakerIa("ia-resumo", props.circuitBreaker(props.getResumo()), clock);
    }

    public ResumoHistoricoDTO gerar(Pet pet, List<HistoricoAtendimento> historico) {
        List<String> descricoes = historico.stream()
                .map(HistoricoAtendimento::getDescricao)
                .filter(descricao -> descricao != null && !descricao.isBlank())
                .toList();
        ResultadoResumo resultado = gerarComFallback(pet, descricoes);
        String resumoGerado = resultado.texto();

        StringBuilder resumoFinal = new StringBuilder(resumoGerado);
        String itensCriticos = pet.getAlergiasCriticas() == null ? "" : pet.getAlergiasCriticas();
        if (!itensCriticos.isBlank()) {
            resumoFinal.append(" ATENCAO - itens criticos: ").append(itensCriticos).append(".");
        }

        String link = "/pets/" + pet.getId() + "/historico";
        return new ResumoHistoricoDTO(
                resumoFinal.toString(), itensCriticos, link, resultado.usouFallback());
    }

    private ResultadoResumo gerarComFallback(Pet pet, List<String> descricoes) {
        String itensCriticos = pet.getAlergiasCriticas() == null ? "" : pet.getAlergiasCriticas();
        if (!circuitBreaker.permitirChamada()) {
            log.warn("Circuit breaker do resumo aberto - usando resumo deterministico sem chamar o provedor");
            return new ResultadoResumo(fallback(pet, descricoes), true);
        }
        Future<String> future = null;
        try {
            future = executor.submit(() -> clienteIa.resumir(pet.getNome(), descricoes, itensCriticos));
            String resumo = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (resumo == null || resumo.isBlank()) {
                throw new IllegalStateException("Resumo vazio");
            }
            circuitBreaker.registrarSucesso();
            return new ResultadoResumo(resumo, false);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Timeout ao gerar resumo do historico; usando fallback deterministico");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | RuntimeException e) {
            log.warn("Falha ao gerar resumo do historico: {}; usando fallback deterministico",
                    e.getClass().getSimpleName());
        }
        circuitBreaker.registrarFalha();
        return new ResultadoResumo(fallback(pet, descricoes), true);
    }

    public CircuitBreakerIa.Metricas obterMetricas() {
        return circuitBreaker.obterMetricas();
    }

    private String fallback(Pet pet, List<String> descricoes) {
        if (descricoes.isEmpty()) {
            return pet.getNome() + " ainda nao possui atendimentos anteriores registrados.";
        }
        String ultimos = String.join("; ", descricoes.stream().limit(3).toList());
        return String.format("%s ja teve %d atendimento(s) registrados. Mais recentes: %s.",
                pet.getNome(), descricoes.size(), ultimos);
    }

    @PreDestroy
    public void encerrarExecutor() {
        executor.shutdownNow();
    }

    private record ResultadoResumo(String texto, boolean usouFallback) {}
}
