package com.petcuidar.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EscalaVeterinarioDTO(
        @NotNull @Valid @Size(max = 100) List<@NotNull HorarioAtendimentoDTO> horarios) {}