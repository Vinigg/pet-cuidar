package com.petcuidar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO para registro de novo atendimento veterinário.
 * Veterinário autorizado registra descrição clínica vinculada ao pet e à consulta.
 */
public class RegistroAtendimentoRequestDTO {

    @NotNull(message = "ID do pet é obrigatório")
    private Long petId;

    @NotNull(message = "ID da consulta é obrigatório")
    private Long consultaId;

    @NotBlank(message = "Descrição clínica é obrigatória")
    @Size(min = 10, max = 2000, message = "Descrição deve ter entre 10 e 2000 caracteres")
    private String descricaoClinica;

    @NotNull(message = "ID do veterinário é obrigatório")
    private Long veterinarioId;

    public Long getPetId() {
        return petId;
    }

    public void setPetId(Long petId) {
        this.petId = petId;
    }

    public Long getConsultaId() {
        return consultaId;
    }

    public void setConsultaId(Long consultaId) {
        this.consultaId = consultaId;
    }

    public String getDescricaoClinica() {
        return descricaoClinica;
    }

    public void setDescricaoClinica(String descricaoClinica) {
        this.descricaoClinica = descricaoClinica;
    }

    public Long getVeterinarioId() {
        return veterinarioId;
    }

    public void setVeterinarioId(Long veterinarioId) {
        this.veterinarioId = veterinarioId;
    }
}
