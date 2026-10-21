package com.petcuidar.service;

import com.petcuidar.dto.PetCadastroDTO;
import com.petcuidar.dto.TutorCadastroDTO;
import com.petcuidar.model.Pet;
import com.petcuidar.model.Tutor;
import com.petcuidar.repository.TutorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TutorService {

    private final TutorRepository tutorRepository;

    public TutorService(TutorRepository tutorRepository) {
        this.tutorRepository = tutorRepository;
    }

    // US1.1: o cadastro so e salvo com e-mail valido (garantido pelo @Valid no
    // controller) e pelo menos um pet vinculado (garantido pelo @NotEmpty no DTO).
    @Transactional
    public Tutor cadastrar(TutorCadastroDTO dto) {
        Tutor tutor = new Tutor();
        tutor.setNome(dto.getNome());
        tutor.setEmail(dto.getEmail());
        tutor.setTelefone(dto.getTelefone());
        tutor.setCpf(dto.getCpf());

        for (PetCadastroDTO petDto : dto.getPets()) {
            Pet pet = new Pet();
            pet.setTutor(tutor);
            pet.setNome(petDto.getNome());
            pet.setEspecie(petDto.getEspecie());
            pet.setIdade(petDto.getIdade());
            pet.setAlergiasCriticas(petDto.getAlergiasCriticas());
            tutor.getPets().add(pet);
        }

        return tutorRepository.save(tutor);
    }
}
