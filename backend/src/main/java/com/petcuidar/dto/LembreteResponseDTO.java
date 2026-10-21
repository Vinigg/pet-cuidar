package com.petcuidar.dto;

import com.petcuidar.model.Lembrete;
import com.petcuidar.model.StatusEnvioLembrete;

public class LembreteResponseDTO {
    private StatusEnvioLembrete status;
    private String textoEnviado;
    private boolean usouFallback;

    public static LembreteResponseDTO from(Lembrete l) {
        LembreteResponseDTO dto = new LembreteResponseDTO();
        dto.status = l.getStatusEnvio();
        dto.textoEnviado = l.getTextoValidado();
        dto.usouFallback = l.isUsouFallback();
        return dto;
    }

    public StatusEnvioLembrete getStatus() { return status; }
    public String getTextoEnviado() { return textoEnviado; }
    public boolean isUsouFallback() { return usouFallback; }
}
