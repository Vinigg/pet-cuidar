package com.petcuidar.service.ia;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerIaTest {

    /** Relogio controlado pelo teste. */
    static class RelogioManual extends Clock {
        private long millis = 1_000_000;
        void avancar(long ms) { millis += ms; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }

    private final RelogioManual relogio = new RelogioManual();
    private final CircuitBreakerIa cb = new CircuitBreakerIa("teste", 2, 1000, relogio);

    @Test
    void abreAposMaxFalhasSeguidasEBloqueiaChamadas() {
        assertTrue(cb.permitirChamada()); cb.registrarFalha();
        assertEquals(CircuitBreakerIa.Estado.FECHADO, cb.estado());
        assertTrue(cb.permitirChamada()); cb.registrarFalha();

        assertEquals(CircuitBreakerIa.Estado.ABERTO, cb.estado());
        assertFalse(cb.permitirChamada());
        assertEquals(1, cb.obterMetricas().chamadasBloqueadas());
        assertEquals(2, cb.obterMetricas().falhas());
    }

    @Test
    void sucessoZeraSequenciaDeFalhas() {
        cb.registrarFalha();
        cb.registrarSucesso();
        cb.registrarFalha();
        assertEquals(CircuitBreakerIa.Estado.FECHADO, cb.estado());
        assertEquals(1, cb.obterMetricas().falhasConsecutivas());
        assertEquals(1, cb.obterMetricas().sucessos());
    }

    @Test
    void aposResetLiberaUmaUnicaChamadaDeTesteESucessoFecha() {
        cb.registrarFalha(); cb.registrarFalha();
        relogio.avancar(999);
        assertFalse(cb.permitirChamada());

        relogio.avancar(1);
        assertTrue(cb.permitirChamada());
        assertEquals(CircuitBreakerIa.Estado.TESTE, cb.estado());
        assertFalse(cb.permitirChamada(), "so uma chamada de prova por vez");

        cb.registrarSucesso();
        assertEquals(CircuitBreakerIa.Estado.FECHADO, cb.estado());
        assertEquals(0, cb.obterMetricas().falhasConsecutivas());
        assertTrue(cb.permitirChamada());
    }

    @Test
    void falhaNaChamadaDeTesteReabreOCircuito() {
        cb.registrarFalha(); cb.registrarFalha();
        relogio.avancar(1000);
        assertTrue(cb.permitirChamada());

        cb.registrarFalha();
        assertEquals(CircuitBreakerIa.Estado.ABERTO, cb.estado());
        assertFalse(cb.permitirChamada());
        relogio.avancar(1000);
        assertTrue(cb.permitirChamada());
    }

    @Test
    void instanciasSaoIndependentes() {
        CircuitBreakerIa outro = new CircuitBreakerIa("outro", 2, 1000, relogio);
        cb.registrarFalha(); cb.registrarFalha();
        assertEquals(CircuitBreakerIa.Estado.ABERTO, cb.estado());
        assertEquals(CircuitBreakerIa.Estado.FECHADO, outro.estado());
        assertTrue(outro.permitirChamada());
    }
}
