package com.petcuidar.dto;

import java.util.List;

public record TutorResponseDTO(
        Long id,
        String nome,
        String email,
        String telefone,
        List<PetResponseDTO> pets
) {
}