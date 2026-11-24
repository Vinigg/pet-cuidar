package com.petcuidar.dto;

import com.petcuidar.model.Lembrete;

public record EstadoLembreteDTO(String status, String textoEnviado, boolean usouFallback,
                                int tentativas, String ultimoErro) {
    public static EstadoLembreteDTO from(Lembrete lembrete) {
        return new EstadoLembreteDTO(lembrete.getStatusEnvio().name(), lembrete.getTextoValidado(),
                lembrete.isUsouFallback(), lembrete.getTentativas(), lembrete.getUltimoErro());
    }
}
