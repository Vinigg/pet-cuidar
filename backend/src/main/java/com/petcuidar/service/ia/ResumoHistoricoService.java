package com.petcuidar.service.ia;

import com.petcuidar.dto.ResumoHistoricoDTO;
import com.petcuidar.model.HistoricoAtendimento;
import com.petcuidar.model.Pet;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Resumo do historico do pet para o veterinario (US5.1).
 *
 * Criterio de aceite de IA mais importante desta funcionalidade: itens
 * marcados como criticos (pet.alergiasCriticas) NUNCA podem depender do
 * texto livre gerado pela IA - eles sao sempre concatenados por regra fixa
 * ao final do resumo, garantindo que uma omissao do modelo nunca esconda
 * uma alergia do veterinario. O resumo tambem sempre acompanha um link
 * para o historico completo.
 */
@Service
public class ResumoHistoricoService {

    public ResumoHistoricoDTO gerar(Pet pet, List<HistoricoAtendimento> historico) {
        String resumoGerado = simularResumoIa(pet, historico);

        StringBuilder resumoFinal = new StringBuilder(resumoGerado);
        if (pet.getAlergiasCriticas() != null && !pet.getAlergiasCriticas().isBlank()) {
            resumoFinal.append(" ATENCAO - itens criticos: ").append(pet.getAlergiasCriticas()).append(".");
        }

        String link = "/pets/" + pet.getId() + "/historico";
        return new ResumoHistoricoDTO(resumoFinal.toString(), link);
    }

    /**
     * Simulacao local do resumo que um LLM geraria a partir do prompt
     * "Gerar resumo do historico do pet" (documento, Secao 5.1). Em producao,
     * este metodo chamaria a API do provedor de IA passando a lista de
     * atendimentos anteriores como contexto.
     */
    private String simularResumoIa(Pet pet, List<HistoricoAtendimento> historico) {
        if (historico.isEmpty()) {
            return pet.getNome() + " ainda nao possui atendimentos anteriores registrados.";
        }
        String ultimos = historico.stream()
                .limit(3)
                .map(HistoricoAtendimento::getDescricao)
                .collect(Collectors.joining("; "));
        return String.format("%s ja teve %d atendimento(s) registrados. Mais recentes: %s.",
                pet.getNome(), historico.size(), ultimos);
    }
}
