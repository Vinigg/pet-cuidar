package com.petcuidar.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Corpo de PATCH /consultas/{id}/reagendamento (PC-028). */
public record ReagendamentoRequestDTO(@NotNull LocalDateTime dataHora) {}
