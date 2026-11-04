package com.petcuidar.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record BloqueioAgendaRequestDTO(@NotNull LocalDateTime inicio, @NotNull LocalDateTime fim,
                                       @Size(max = 255) String motivo) {}