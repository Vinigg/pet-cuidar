package com.petcuidar.service;

import com.petcuidar.model.Consulta;
import com.petcuidar.model.StatusEnvioLembrete;
import com.petcuidar.repository.ConsultaRepository;
import com.petcuidar.repository.LembreteRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LembreteSchedulerTest {

    @Test
    void ignoraEnviadoEContinuaQuandoUmaConsultaFalha() {
        ConsultaRepository consultas = mock(ConsultaRepository.class);
        LembreteRepository lembretes = mock(LembreteRepository.class);
        LembreteDeliveryService delivery = mock(LembreteDeliveryService.class);
        Consulta enviado = consulta(1L), falha = consulta(2L), sucesso = consulta(3L);
        when(consultas.findByDataHoraBetweenAndStatusInOrderByDataHoraAsc(any(), any(), any()))
                .thenReturn(List.of(enviado, falha, sucesso));
        when(lembretes.existsByConsultaIdAndStatusEnvio(1L, StatusEnvioLembrete.ENVIADO)).thenReturn(true);
        doThrow(new IllegalStateException("falha isolada")).when(delivery).disparar(2L);
        LembreteScheduler scheduler = new LembreteScheduler(consultas, lembretes, delivery, 24, 5);

        scheduler.processarEm(LocalDateTime.of(2026, 11, 3, 12, 0));

        verify(delivery, never()).disparar(1L);
        verify(delivery).disparar(2L);
        verify(delivery).disparar(3L);
        verify(consultas).findByDataHoraBetweenAndStatusInOrderByDataHoraAsc(
                eq(LocalDateTime.of(2026, 11, 3, 12, 0)),
                eq(LocalDateTime.of(2026, 11, 4, 12, 5)), any());
    }

    private Consulta consulta(long id) {
        Consulta consulta = new Consulta();
        consulta.setId(id);
        return consulta;
    }
}
