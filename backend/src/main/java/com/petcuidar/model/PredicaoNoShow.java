package com.petcuidar.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "predicoes_no_show")
@Getter
@Setter
@NoArgsConstructor
public class PredicaoNoShow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "consulta_id", nullable = false, unique = true)
    private Consulta consulta;

    /** Score de 0.0 (baixo risco) a 1.0 (alto risco). */
    private double score;

    /** Explicacao curta e verificavel do motivo do score (nunca so um numero). */
    @Column(length = 500)
    private String motivo;

    private LocalDateTime dataCalculo;
}
