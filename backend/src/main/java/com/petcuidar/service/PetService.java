package com.petcuidar.service;

import com.petcuidar.dto.ResumoHistoricoDTO;
import com.petcuidar.exception.RecursoNaoEncontradoException;
import com.petcuidar.model.HistoricoAtendimento;
import com.petcuidar.model.Pet;
import com.petcuidar.repository.HistoricoAtendimentoRepository;
import com.petcuidar.repository.PetRepository;
import com.petcuidar.service.ia.ResumoHistoricoService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PetService {

    private final PetRepository petRepository;
    private final HistoricoAtendimentoRepository historicoRepository;
    private final ResumoHistoricoService resumoHistoricoService;

    public PetService(PetRepository petRepository,
                       HistoricoAtendimentoRepository historicoRepository,
                       ResumoHistoricoService resumoHistoricoService) {
        this.petRepository = petRepository;
        this.historicoRepository = historicoRepository;
        this.resumoHistoricoService = resumoHistoricoService;
    }

    public List<HistoricoAtendimento> listarHistorico(Long petId) {
        buscarPet(petId);
        return historicoRepository.findByPetIdOrderByDataDesc(petId);
    }

    public ResumoHistoricoDTO obterResumo(Long petId) {
        Pet pet = buscarPet(petId);
        List<HistoricoAtendimento> historico = historicoRepository.findByPetIdOrderByDataDesc(petId);
        return resumoHistoricoService.gerar(pet, historico);
    }

    private Pet buscarPet(Long id) {
        return petRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pet nao encontrado: id " + id));
    }
}
