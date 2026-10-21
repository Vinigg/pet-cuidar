package com.petcuidar.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pets")
@Getter
@Setter
@NoArgsConstructor
public class Pet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tutor_id", nullable = false)
    private Tutor tutor;

    @NotBlank
    private String nome;

    private String especie;

    private Integer idade;

    /**
     * Itens criticos de saude (alergias, reacoes adversas). Este campo NUNCA
     * pode ser omitido do resumo gerado por IA - a regra de negocio que
     * garante isso fica em ResumoHistoricoService, fora do texto livre
     * gerado pelo modelo.
     */
    @Column(length = 1000)
    private String alergiasCriticas;
}
