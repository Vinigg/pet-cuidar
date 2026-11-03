package com.petcuidar.dto;

import com.petcuidar.model.Consulta;
import com.petcuidar.model.Pet;
import com.petcuidar.model.StatusConsulta;
import com.petcuidar.model.Tutor;
import com.petcuidar.model.Veterinario;

import java.time.LocalDateTime;

/**
 * Detalhamento de uma consulta (GET /consultas/{id}). Somente leitura e
 * limitado aos atributos que existem no modelo. CPF do tutor e a versao de
 * bloqueio do veterinario ficam de fora por nao serem necessarios na tela.
 */
public record ConsultaDetalheDTO(
        Long id,
        LocalDateTime dataHora,
        StatusConsulta status,
        PetDetalhe pet,
        TutorDetalhe tutor,
        VeterinarioDetalhe veterinario
) {

    public record PetDetalhe(Long id, String nome, String especie, Integer idade, String alergiasCriticas) {}

    public record TutorDetalhe(Long id, String nome, String email, String telefone) {}

    public record VeterinarioDetalhe(Long id, String nome, String especialidade) {}

    public static ConsultaDetalheDTO from(Consulta consulta) {
        Pet pet = consulta.getPet();
        Tutor tutor = pet.getTutor();
        Veterinario vet = consulta.getVeterinario();
        return new ConsultaDetalheDTO(
                consulta.getId(),
                consulta.getDataHora(),
                consulta.getStatus(),
                new PetDetalhe(pet.getId(), pet.getNome(), pet.getEspecie(), pet.getIdade(), pet.getAlergiasCriticas()),
                new TutorDetalhe(tutor.getId(), tutor.getNome(), tutor.getEmail(), tutor.getTelefone()),
                new VeterinarioDetalhe(vet.getId(), vet.getNome(), vet.getEspecialidade()));
    }
}
