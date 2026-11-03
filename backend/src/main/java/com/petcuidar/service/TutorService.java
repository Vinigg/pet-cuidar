package com.petcuidar.service;

import com.petcuidar.dto.PetCadastroDTO;
import com.petcuidar.dto.PetResponseDTO;
import com.petcuidar.dto.TutorCadastroDTO;
import com.petcuidar.dto.TutorResponseDTO;
import com.petcuidar.exception.CadastroConflitanteException;
import org.springframework.dao.DataIntegrityViolationException;
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
    public TutorResponseDTO cadastrar(TutorCadastroDTO dto) {
        String cpf = dto.getCpf() == null || dto.getCpf().isBlank()
                ? null : dto.getCpf().trim();
        if (tutorRepository.existsByEmail(dto.getEmail())) {
            throw new CadastroConflitanteException("E-mail ja cadastrado.");
        }
        if (cpf != null && tutorRepository.existsByCpf(cpf)) {
            throw new CadastroConflitanteException("CPF ja cadastrado.");
        }

        Tutor tutor = new Tutor();
        tutor.setNome(dto.getNome());
        tutor.setEmail(dto.getEmail());
        tutor.setTelefone(dto.getTelefone());
        tutor.setCpf(cpf);

        for (PetCadastroDTO petDto : dto.getPets()) {
            Pet pet = new Pet();
            pet.setTutor(tutor);
            pet.setNome(petDto.getNome());
            pet.setEspecie(petDto.getEspecie());
            pet.setIdade(petDto.getIdade());
            pet.setAlergiasCriticas(petDto.getAlergiasCriticas());
            tutor.getPets().add(pet);
        }

        Tutor tutorSalvo;
        try {
            // Flush faz as restricoes do banco serem verificadas nesta transacao.
            tutorSalvo = tutorRepository.saveAndFlush(tutor);
        } catch (DataIntegrityViolationException ex) {
            throw new CadastroConflitanteException(
                    "Cadastro em conflito com os dados existentes.", ex);
        }

        var petsResponse = tutorSalvo.getPets().stream()
                .map(pet -> new PetResponseDTO(
                        pet.getId(),
                        pet.getNome(),
                        pet.getEspecie(),
                        pet.getIdade(),
                        pet.getAlergiasCriticas()
                ))
                .toList();

        return new TutorResponseDTO(
                tutorSalvo.getId(),
                tutorSalvo.getNome(),
                tutorSalvo.getEmail(),
                tutorSalvo.getTelefone(),
                petsResponse
        );
    }
}
