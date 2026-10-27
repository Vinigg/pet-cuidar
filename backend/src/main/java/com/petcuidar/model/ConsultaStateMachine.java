package com.petcuidar.model;

import com.petcuidar.exception.TransicaoEstadoInvalidaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Valida e aplica exclusivamente as transições permitidas da consulta. */
@Component
public class ConsultaStateMachine {
    private static final Map<StatusConsulta, Set<StatusConsulta>> TRANSICOES = new EnumMap<>(StatusConsulta.class);
    static {
        TRANSICOES.put(StatusConsulta.AGENDADA, EnumSet.of(StatusConsulta.CONFIRMADA,
                StatusConsulta.REALIZADA, StatusConsulta.CANCELADA, StatusConsulta.NAO_COMPARECEU));
        TRANSICOES.put(StatusConsulta.CONFIRMADA, EnumSet.of(StatusConsulta.REALIZADA,
                StatusConsulta.CANCELADA, StatusConsulta.NAO_COMPARECEU));
        TRANSICOES.put(StatusConsulta.REALIZADA, EnumSet.noneOf(StatusConsulta.class));
        TRANSICOES.put(StatusConsulta.CANCELADA, EnumSet.noneOf(StatusConsulta.class));
        TRANSICOES.put(StatusConsulta.NAO_COMPARECEU, EnumSet.noneOf(StatusConsulta.class));
    }

    private final Clock clock;
    private final Duration tolerancia;

    @Autowired
    public ConsultaStateMachine(@Value("${consulta.tolerancia-nao-comparecimento-minutos:0}") long toleranciaMinutos) {
        this(Clock.systemDefaultZone(), Duration.ofMinutes(Math.max(0, toleranciaMinutos)));
    }

    public ConsultaStateMachine(Clock clock, Duration tolerancia) {
        this.clock = clock;
        this.tolerancia = tolerancia;
    }

    public void transicionar(Consulta consulta, StatusConsulta destino) {
        StatusConsulta origem = consulta.getStatus();
        if (origem == null || destino == null || !TRANSICOES.get(origem).contains(destino)) {
            throw new TransicaoEstadoInvalidaException("Transicao de status nao permitida.");
        }
        LocalDateTime agora = LocalDateTime.now(clock);
        if (destino == StatusConsulta.CANCELADA && !agora.isBefore(consulta.getDataHora())) {
            throw new TransicaoEstadoInvalidaException("O cancelamento so e permitido antes do horario da consulta.");
        }
        if (destino == StatusConsulta.NAO_COMPARECEU &&
                agora.isBefore(consulta.getDataHora().plus(tolerancia))) {
            throw new TransicaoEstadoInvalidaException("A consulta ainda esta dentro do periodo de tolerancia.");
        }
        consulta.aplicarStatusValidado(destino);
    }
}
