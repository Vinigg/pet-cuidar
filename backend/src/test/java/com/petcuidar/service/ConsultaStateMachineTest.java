package com.petcuidar.service;

import com.petcuidar.exception.TransicaoEstadoInvalidaException;
import com.petcuidar.model.Consulta;
import com.petcuidar.model.ConsultaStateMachine;
import com.petcuidar.model.StatusConsulta;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ConsultaStateMachineTest {
    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
    private Consulta consulta(String hora) {
        Consulta c = new Consulta(); c.setDataHora(LocalDateTime.parse(hora)); return c;
    }
    private ConsultaStateMachine machine(long tolerance) {
        return new ConsultaStateMachine(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(tolerance));
    }

    @Test void permiteFluxoEAtendimentoSemConfirmacao() {
        Consulta c = consulta("2026-10-27T13:00:00");
        machine(0).transicionar(c, StatusConsulta.CONFIRMADA);
        machine(0).transicionar(c, StatusConsulta.REALIZADA);
        assertEquals(StatusConsulta.REALIZADA, c.getStatus());
        Consulta semConfirmacao = consulta("2026-10-27T13:00:00");
        machine(0).transicionar(semConfirmacao, StatusConsulta.REALIZADA);
        assertEquals(StatusConsulta.REALIZADA, semConfirmacao.getStatus());
    }

    @Test void respeitaCancelamentoEJanelaDeAusencia() {
        Consulta cancel = consulta("2026-10-27T13:00:00");
        machine(0).transicionar(cancel, StatusConsulta.CANCELADA);
        Consulta absent = consulta("2026-10-27T11:00:00");
        machine(0).transicionar(absent, StatusConsulta.CONFIRMADA);
        assertThrows(TransicaoEstadoInvalidaException.class, () -> machine(90).transicionar(absent, StatusConsulta.NAO_COMPARECEU));
        machine(30).transicionar(absent, StatusConsulta.NAO_COMPARECEU);
        assertEquals(StatusConsulta.NAO_COMPARECEU, absent.getStatus());
    }

    @Test void rejeitaEstadoFinalEPreservaStatusAoFalhar() {
        Consulta c = consulta("2026-10-27T13:00:00");
        machine(0).transicionar(c, StatusConsulta.CANCELADA);
        assertThrows(TransicaoEstadoInvalidaException.class, () -> machine(0).transicionar(c, StatusConsulta.AGENDADA));
        assertEquals(StatusConsulta.CANCELADA, c.getStatus());

        Consulta realizada = consulta("2026-10-27T13:00:00");
        machine(0).transicionar(realizada, StatusConsulta.REALIZADA);
        assertThrows(TransicaoEstadoInvalidaException.class,
                () -> machine(0).transicionar(realizada, StatusConsulta.CONFIRMADA));
        assertEquals(StatusConsulta.REALIZADA, realizada.getStatus());

        Consulta ausente = consulta("2026-10-27T11:00:00");
        machine(0).transicionar(ausente, StatusConsulta.NAO_COMPARECEU);
        assertThrows(TransicaoEstadoInvalidaException.class,
                () -> machine(0).transicionar(ausente, StatusConsulta.AGENDADA));
        assertEquals(StatusConsulta.NAO_COMPARECEU, ausente.getStatus());
    }
}
