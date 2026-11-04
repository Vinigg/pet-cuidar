package com.petcuidar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VeterinarioAtualizacaoDTO(@NotBlank @Size(max = 255) String nome,
                                        @Size(max = 255) String especialidade, @NotNull Boolean ativo) {}