package com.petcuidar.service.ia;

import com.petcuidar.config.IaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.concurrent.atomic.LongAdder;

/**
 * Circuit breaker simples, em memoria, usado por cada integracao de IA
 * (lembrete, resumo e predicao), cada uma com a sua instancia.
 *
 * <ul>
 *   <li>FECHADO: chamadas liberadas; falhas seguidas sao contadas.</li>
 *   <li>ABERTO: apos {@code maxFalhas} falhas seguidas, nenhuma chamada sai
 *       durante {@code resetMs}; o servico vai direto para o fallback.</li>
 *   <li>TESTE: passado o intervalo, uma unica chamada de prova e liberada.
 *       Sucesso fecha o circuito; falha reabre.</li>
 * </ul>
 *
 * O estado e local a instancia da aplicacao (suficiente para o deploy unico
 * com SQLite). O {@link Clock} e injetavel para testes deterministicos.
 */
public class CircuitBreakerIa {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerIa.class);

    public enum Estado { FECHADO, ABERTO, TESTE }

    public record Metricas(Estado estado, int falhasConsecutivas, long falhas,
                           long sucessos, long chamadasBloqueadas) {}

    private final String nome;
    private final int maxFalhas;
    private final long resetMs;
    private final Clock clock;

    private Estado estado = Estado.FECHADO;
    private int falhasSeguidas = 0;
    private long abertoAte = 0;
    private final LongAdder falhas = new LongAdder(), sucessos = new LongAdder(), bloqueadas = new LongAdder();

    public CircuitBreakerIa(String nome, int maxFalhas, long resetMs, Clock clock) {
        this.nome = nome;
        this.maxFalhas = Math.max(1, maxFalhas);
        this.resetMs = Math.max(0, resetMs);
        this.clock = clock;
    }

    public CircuitBreakerIa(String nome, IaProperties.CircuitBreaker config, Clock clock) {
        this(nome, config.getMaxFalhas(), config.getResetMs(), clock);
    }

    /**
     * Indica se a chamada ao servico externo pode sair agora. Quando devolve
     * {@code false}, o chamador deve usar o fallback sem chamar o servico.
     */
    public synchronized boolean permitirChamada() {
        if (estado == Estado.ABERTO) {
            if (clock.millis() < abertoAte) {
                bloqueadas.increment();
                return false;
            }
            estado = Estado.TESTE;
            log.info("Circuit breaker '{}' em tentativa de recuperacao", nome);
            return true;
        }
        if (estado == Estado.TESTE) {
            // Ja existe uma chamada de prova em andamento.
            bloqueadas.increment();
            return false;
        }
        return true;
    }

    public synchronized void registrarSucesso() {
        if (estado != Estado.FECHADO) log.info("Circuit breaker '{}' FECHADO apos recuperacao", nome);
        estado = Estado.FECHADO;
        falhasSeguidas = 0;
        sucessos.increment();
    }

    public synchronized void registrarFalha() {
        falhasSeguidas++;
        falhas.increment();
        if (falhasSeguidas >= maxFalhas) {
            abertoAte = clock.millis() + resetMs;
            boolean reaberto = estado != Estado.ABERTO;
            estado = Estado.ABERTO;
            if (reaberto) log.warn("Circuit breaker '{}' ABERTO por {} ms apos {} falhas seguidas",
                    nome, resetMs, falhasSeguidas);
        }
    }

    public synchronized Estado estado() {
        return estado;
    }

    public synchronized Metricas obterMetricas() {
        return new Metricas(estado, falhasSeguidas, falhas.sum(), sucessos.sum(), bloqueadas.sum());
    }

    public String nome() {
        return nome;
    }
}
