package com.petcuidar.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "horarios_atendimento_veterinario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HorarioAtendimentoVeterinario {
    public static final int DURACAO_MINUTOS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veterinario_id", nullable = false)
    private Veterinario veterinario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DayOfWeek diaSemana;

    @Convert(converter = HorarioLocalConverter.class)
    @Column(nullable = false, length = 5)
    private LocalTime horaInicio;

    @Convert(converter = HorarioLocalConverter.class)
    @Column(nullable = false, length = 5)
    private LocalTime horaFim;

    public HorarioAtendimentoVeterinario(Veterinario veterinario, DayOfWeek diaSemana,
                                         LocalTime horaInicio, LocalTime horaFim) {
        if (diaSemana == null || !naGrade(horaInicio) || !naGrade(horaFim)
                || !horaInicio.isBefore(horaFim)) {
            throw new IllegalArgumentException(
                    "Informe dia e periodo valido, em intervalos de 30 minutos, sem atravessar a meia-noite.");
        }
        this.veterinario = veterinario;
        this.diaSemana = diaSemana;
        this.horaInicio = horaInicio;
        this.horaFim = horaFim;
    }

    public boolean atende(LocalDateTime dataHora) {
        return dataHora != null && dataHora.getDayOfWeek() == diaSemana
                && naGrade(dataHora.toLocalTime())
                && !dataHora.toLocalTime().isBefore(horaInicio)
                && !dataHora.plusMinutes(DURACAO_MINUTOS).toLocalDate().isAfter(dataHora.toLocalDate())
                && !dataHora.plusMinutes(DURACAO_MINUTOS).toLocalTime().isAfter(horaFim);
    }

    private static boolean naGrade(LocalTime hora) {
        return hora != null && hora.getMinute() % DURACAO_MINUTOS == 0
                && hora.getSecond() == 0 && hora.getNano() == 0;
    }
}