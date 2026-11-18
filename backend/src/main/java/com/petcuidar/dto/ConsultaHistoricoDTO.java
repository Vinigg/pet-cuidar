package com.petcuidar.dto;

import com.petcuidar.model.Consulta;
import com.petcuidar.model.StatusConsulta;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Consulta do pet com os atendimentos clinicos registrados nela.
 */
public record ConsultaHistoricoDTO(
        Long consultaId,
        LocalDateTime dataHora,
        StatusConsulta status,
        Long veterinarioId,
        String veterinarioNome,
        List<AtendimentoResponseDTO> atendimentos
) {
    public static ConsultaHistoricoDTO from(Consulta consulta, List<AtendimentoResponseDTO> atendimentos) {
        return new ConsultaHistoricoDTO(
                consulta.getId(),
                consulta.getDataHora(),
                consulta.getStatus(),
                consulta.getVeterinario() != null ? consulta.getVeterinario().getId() : null,
                consulta.getVeterinario() != null ? consulta.getVeterinario().getNome() : null,
                atendimentos
        );
    }
}
