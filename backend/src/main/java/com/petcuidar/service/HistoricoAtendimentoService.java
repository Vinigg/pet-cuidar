package com.petcuidar.service;

import com.petcuidar.model.HistoricoAtendimento;
import com.petcuidar.repository.HistoricoAtendimentoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class HistoricoAtendimentoService {

    private final HistoricoAtendimentoRepository historicoRepository;

    public HistoricoAtendimentoService(HistoricoAtendimentoRepository historicoRepository) {
        this.historicoRepository = historicoRepository;
    }

    public List<HistoricoAtendimento> buscarHistoricoPorPet(Long petId) {
        return historicoRepository.findByPetIdOrderByDataDesc(petId);
    }
    
    public String montarDadosParaIA(Long petId) {
        List<HistoricoAtendimento> historico = buscarHistoricoPorPet(petId);

        if (historico.isEmpty()) {
            return "Sem histórico prévio.";
        }

        return historico.stream()
            .map(h -> "Data: " + h.getData() + " - Descrição: " + h.getDescricao())
            .collect(Collectors.joining(" | "));
    }
}