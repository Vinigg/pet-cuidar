package com.petcuidar.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record VeterinarioCadastroDTO(@NotBlank @Size(max = 255) String nome,
                                     @Size(max = 255) String especialidade,
                                     @Valid @Size(max = 100) List<@NotNull HorarioAtendimentoDTO> horarios) {}