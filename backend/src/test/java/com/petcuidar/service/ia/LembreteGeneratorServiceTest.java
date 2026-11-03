package com.petcuidar.service.ia;

import com.petcuidar.config.IaProperties;
import com.petcuidar.model.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LembreteGeneratorServiceTest {
    private Consulta consulta() {
        Tutor tutor = new Tutor(); tutor.setNome("Ana");
        Pet pet = new Pet(); pet.setNome("Rex"); pet.setTutor(tutor);
        Veterinario vet = new Veterinario(); vet.setNome("Dra. Bia");
        Consulta c = new Consulta(); c.setPet(pet); c.setVeterinario(vet);
        c.setDataHora(LocalDateTime.of(2026, 10, 28, 15, 30)); return c;
    }
    private IaProperties properties(long timeout, int failures, long reset) {
        IaProperties p = new IaProperties(); p.setTimeoutMs(timeout);
        p.getCircuitBreaker().setMaxFalhas(failures); p.getCircuitBreaker().setResetMs(reset); return p;
    }

    @Test void sucessoValidoEInvalidoAtualizamContadoresCorretamente() {
        IaProperties props = properties(500, 3, 30000);
        LembreteGeneratorService ok = new LembreteGeneratorService(props,
                (tutor, pet, data, vet) -> "Ana Rex consulta 28/10/2026 as 15:30");
        try {
            var result = ok.gerar(consulta());
            assertFalse(result.usouFallback);
            assertEquals(1, ok.obterMetricas().sucessos());
            assertEquals(0, ok.obterMetricas().falhasConsecutivas());
        } finally { ok.encerrarExecutor(); }

        LembreteGeneratorService invalid = new LembreteGeneratorService(props,
                (tutor, pet, data, vet) -> "texto sem dados obrigatorios");
        try {
            var result = invalid.gerar(consulta());
            assertTrue(result.usouFallback);
            assertTrue(result.textoValidado.contains("Rex"));
            assertEquals(1, invalid.obterMetricas().respostasInvalidas());
            assertEquals(0, invalid.obterMetricas().sucessos());
        } finally { invalid.encerrarExecutor(); }
    }

    @Test void falhasAbremCircuitoEBloqueiamNovasChamadas() {
        AtomicInteger calls = new AtomicInteger();
        LembreteGeneratorService service = new LembreteGeneratorService(properties(500, 3, 30000),
                (t, p, d, v) -> { calls.incrementAndGet(); throw new IllegalStateException("offline"); });
        try {
            service.gerar(consulta()); service.gerar(consulta()); service.gerar(consulta());
            assertEquals(LembreteGeneratorService.EstadoCircuito.ABERTO, service.obterMetricas().estado());
            assertTrue(service.gerar(consulta()).usouFallback);
            assertEquals(3, calls.get());
            assertEquals(4, service.obterMetricas().fallbacks());
        } finally { service.encerrarExecutor(); }
    }

    @Test void timeoutCancelaTarefaEUsaFallback() {
        LembreteGeneratorService service = new LembreteGeneratorService(properties(30, 3, 30000),
                (t, p, d, v) -> { try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } return "late"; });
        try {
            assertTrue(service.gerar(consulta()).usouFallback);
            assertEquals(1, service.obterMetricas().timeouts());
            assertEquals(1, service.obterMetricas().falhas());
        } finally { service.encerrarExecutor(); }
    }

    @Test void recuperaAposIntervaloEResetaContador() throws Exception {
        AtomicBoolean success = new AtomicBoolean();
        LembreteGeneratorService service = new LembreteGeneratorService(properties(300, 1, 40),
                (t, p, d, v) -> { if (!success.get()) throw new IllegalStateException("offline"); return "Ana Rex consulta 28/10/2026 as 15:30"; });
        try {
            service.gerar(consulta());
            assertEquals(LembreteGeneratorService.EstadoCircuito.ABERTO, service.obterMetricas().estado());
            Thread.sleep(60); success.set(true);
            assertFalse(service.gerar(consulta()).usouFallback);
            assertEquals(LembreteGeneratorService.EstadoCircuito.FECHADO, service.obterMetricas().estado());
            assertEquals(0, service.obterMetricas().falhasConsecutivas());
        } finally { service.encerrarExecutor(); }
    }

    @Test void permiteSomenteUmaSondaDeRecuperacaoConcorrente() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch probeStarted = new CountDownLatch(1), releaseProbe = new CountDownLatch(1);
        LembreteGeneratorService service = new LembreteGeneratorService(properties(1000, 1, 30), (t, p, d, v) -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("offline");
            probeStarted.countDown();
            try { releaseProbe.await(1, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return "Ana Rex consulta 28/10/2026 as 15:30";
        });
        Thread probe = null;
        try {
            service.gerar(consulta()); Thread.sleep(40);
            probe = new Thread(() -> service.gerar(consulta())); probe.start();
            assertTrue(probeStarted.await(1, TimeUnit.SECONDS));
            assertTrue(service.gerar(consulta()).usouFallback);
            assertEquals(2, calls.get());
            releaseProbe.countDown(); probe.join(1000);
            assertEquals(LembreteGeneratorService.EstadoCircuito.FECHADO, service.obterMetricas().estado());
        } finally { releaseProbe.countDown(); if (probe != null) probe.join(1000); service.encerrarExecutor(); }
    }
}
