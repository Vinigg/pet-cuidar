package com.petcuidar.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "consultas")
@Getter
@NoArgsConstructor
public class Consulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pet_id", nullable = false)
    @Setter
    private Pet pet;

    @ManyToOne
    @JoinColumn(name = "veterinario_id", nullable = false)
    @Setter
    private Veterinario veterinario;

    @Column(name = "data_hora", nullable = false)
    @Setter
    private LocalDateTime dataHora;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private StatusConsulta status = StatusConsulta.AGENDADA;

    void aplicarStatusValidado(StatusConsulta status) { this.status = status; }
}
