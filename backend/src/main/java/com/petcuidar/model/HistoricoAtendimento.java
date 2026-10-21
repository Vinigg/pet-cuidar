package com.petcuidar.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "historico_atendimentos")
@Getter
@Setter
@NoArgsConstructor
public class HistoricoAtendimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @ManyToOne
    @JoinColumn(name = "consulta_id")
    private Consulta consulta;

    @Column(length = 2000)
    private String descricao;

    /** Resumo gerado por IA a partir do historico bruto (ver ResumoHistoricoService). */
    @Column(name = "resumo_ia", length = 1000)
    private String resumoIa;

    private LocalDateTime data;
}
