package com.petcuidar.dto;

import com.petcuidar.model.BloqueioAgendaVeterinario;

import java.time.LocalDateTime;

public record BloqueioAgendaResponseDTO(Long id, LocalDateTime inicio, LocalDateTime fim, String motivo) {
    public static BloqueioAgendaResponseDTO from(BloqueioAgendaVeterinario bloqueio) {
        return new BloqueioAgendaResponseDTO(bloqueio.getId(), bloqueio.getInicio(),
                bloqueio.getFim(), bloqueio.getMotivo());
    }
}