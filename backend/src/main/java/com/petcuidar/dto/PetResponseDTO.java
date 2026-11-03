package com.petcuidar.dto;

public record PetResponseDTO(
        Long id,
        String nome,
        String especie,
        Integer idade,
        String alergiasCriticas
) {
}