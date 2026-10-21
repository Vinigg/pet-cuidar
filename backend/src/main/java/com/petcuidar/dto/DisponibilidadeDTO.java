package com.petcuidar.dto;

import java.time.LocalDateTime;

public class DisponibilidadeDTO {
    private LocalDateTime horario;
    private boolean disponivel;

    public DisponibilidadeDTO(LocalDateTime horario, boolean disponivel) {
        this.horario = horario;
        this.disponivel = disponivel;
    }

    public LocalDateTime getHorario() { return horario; }
    public boolean isDisponivel() { return disponivel; }
}
