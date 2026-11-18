package com.petcuidar.service;

import com.petcuidar.dto.PetOpcaoDTO;
import com.petcuidar.repository.PetRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PetService {

    private final PetRepository petRepository;

    public PetService(PetRepository petRepository) {
        this.petRepository = petRepository;
    }

    public List<PetOpcaoDTO> listarOpcoes(Long tutorId) {
        return petRepository.listarOpcoes(tutorId);
    }
}
