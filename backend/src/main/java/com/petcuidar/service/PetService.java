package com.petcuidar.service;

import com.petcuidar.dto.PetCadastroDTO;
import com.petcuidar.dto.PetOpcaoDTO;
import com.petcuidar.dto.PetResponseDTO;
import com.petcuidar.exception.RecursoNaoEncontradoException;
import com.petcuidar.model.Pet;
import com.petcuidar.repository.PetRepository;
import com.petcuidar.repository.TutorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PetService {
    private final PetRepository pets;
    private final TutorRepository tutores;

    public PetService(PetRepository pets, TutorRepository tutores) {
        this.pets = pets;
        this.tutores = tutores;
    }

    public List<PetOpcaoDTO> listarOpcoes(Long tutorId) {
        return pets.listarOpcoes(tutorId);
    }

    public List<PetOpcaoDTO> listarOpcoesVeterinario(Long veterinarioId) {
        return pets.listarOpcoesVeterinario(veterinarioId);
    }

    public List<PetResponseDTO> listarDoTutor(Long tutorId) {
        return pets.findByTutorIdOrderByNomeAsc(tutorId).stream().map(PetService::resposta).toList();
    }

    @Transactional
    public PetResponseDTO cadastrar(Long tutorId, PetCadastroDTO dados) {
        var tutor = tutores.findById(tutorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tutor não encontrado."));
        var pet = new Pet();
        pet.setTutor(tutor);
        pet.setNome(dados.getNome());
        pet.setEspecie(dados.getEspecie());
        pet.setIdade(dados.getIdade());
        pet.setAlergiasCriticas(dados.getAlergiasCriticas());
        return resposta(pets.save(pet));
    }

    private static PetResponseDTO resposta(Pet pet) {
        return new PetResponseDTO(pet.getId(), pet.getNome(), pet.getEspecie(), pet.getIdade(), pet.getAlergiasCriticas());
    }
}
