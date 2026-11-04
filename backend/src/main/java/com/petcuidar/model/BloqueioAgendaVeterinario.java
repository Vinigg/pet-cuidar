package com.petcuidar.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bloqueios_agenda_veterinario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BloqueioAgendaVeterinario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veterinario_id", nullable = false)
    private Veterinario veterinario;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column(nullable = false)
    private LocalDateTime fim;

    @Column(length = 255)
    private String motivo;

    public BloqueioAgendaVeterinario(Veterinario veterinario, LocalDateTime inicio,
                                     LocalDateTime fim, String motivo) {
        if (inicio == null || fim == null || !inicio.isBefore(fim)) {
            throw new IllegalArgumentException("O inicio do bloqueio deve ser anterior ao fim.");
        }
        this.veterinario = veterinario;
        this.inicio = inicio;
        this.fim = fim;
        this.motivo = motivo;
    }

    public boolean impede(LocalDateTime dataHora) {
        return dataHora.isBefore(fim)
                && dataHora.plusMinutes(HorarioAtendimentoVeterinario.DURACAO_MINUTOS).isAfter(inicio);
    }
}