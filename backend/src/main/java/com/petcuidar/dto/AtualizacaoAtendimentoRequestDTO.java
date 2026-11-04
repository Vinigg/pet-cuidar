package com.petcuidar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO para atualização de atendimento veterinário.
 * Permite que veterinário autorizado atualize a descrição clínica.
 */
public class AtualizacaoAtendimentoRequestDTO {

    @NotNull(message = "ID do atendimento é obrigatório")
    private Long atendimentoId;

    @NotBlank(message = "Descrição clínica é obrigatória")
    @Size(min = 10, max = 2000, message = "Descrição deve ter entre 10 e 2000 caracteres")
    private String descricaoClinica;

    @NotNull(message = "ID do veterinário é obrigatório")
    private Long veterinarioId;

    public Long getAtendimentoId() {
        return atendimentoId;
    }

    public void setAtendimentoId(Long atendimentoId) {
        this.atendimentoId = atendimentoId;
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
