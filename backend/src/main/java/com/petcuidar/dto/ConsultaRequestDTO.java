package com.petcuidar.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class ConsultaRequestDTO {
    @NotNull
    private Long petId;
    @NotNull
    private Long veterinarioId;
    @NotNull
    private LocalDateTime dataHora;

    public Long getPetId() { return petId; }
    public void setPetId(Long petId) { this.petId = petId; }
    public Long getVeterinarioId() { return veterinarioId; }
    public void setVeterinarioId(Long veterinarioId) { this.veterinarioId = veterinarioId; }
    public LocalDateTime getDataHora() { return dataHora; }
    public void setDataHora(LocalDateTime dataHora) { this.dataHora = dataHora; }
}
