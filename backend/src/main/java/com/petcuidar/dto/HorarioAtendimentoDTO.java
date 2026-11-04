package com.petcuidar.dto;

import com.petcuidar.model.HorarioAtendimentoVeterinario;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record HorarioAtendimentoDTO(@NotNull DayOfWeek diaSemana,
                                    @NotNull LocalTime horaInicio, @NotNull LocalTime horaFim) {
    public static HorarioAtendimentoDTO from(HorarioAtendimentoVeterinario horario) {
        return new HorarioAtendimentoDTO(horario.getDiaSemana(), horario.getHoraInicio(), horario.getHoraFim());
    }
}