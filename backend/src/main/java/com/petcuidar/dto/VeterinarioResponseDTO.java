package com.petcuidar.dto;

import com.petcuidar.model.Veterinario;

public record VeterinarioResponseDTO(Long id, String nome, String especialidade, boolean ativo) {
    public static VeterinarioResponseDTO from(Veterinario veterinario) {
        return new VeterinarioResponseDTO(veterinario.getId(), veterinario.getNome(),
                veterinario.getEspecialidade(), veterinario.isAtivo());
    }
}