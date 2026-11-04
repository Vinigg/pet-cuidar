package com.petcuidar.dto;

import java.util.List;

/**
 * Historico completo do pet (US5.1 / PC-050).
 *
 * Contem apenas dados registrados (nada gerado por IA). Os itens criticos vem
 * do cadastro do pet, de forma deterministica, e nao dependem do resumo de IA.
 */
public record HistoricoPetDTO(
        Long petId,
        String petNome,
        String especie,
        Integer idade,
        String tutorNome,
        String itensCriticos,
        List<ConsultaHistoricoDTO> consultas,
        List<AtendimentoResponseDTO> atendimentosSemConsulta
) {
}
