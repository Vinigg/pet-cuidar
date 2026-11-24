package com.petcuidar.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AccessLevel;
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

    @ManyToOne
    @JoinColumn(name = "veterinario_id")
    private Veterinario veterinario;

    @Column(length = 2000)
    private String descricao;

    /** Resumo gerado por IA a partir do historico bruto (ver ResumoHistoricoService). */
    @Column(name = "resumo_ia", length = 1000)
    private String resumoIa;

    private LocalDateTime data;

    /** Data de criação do registro (auditoria) */
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    /** Data da última atualização (auditoria) */
    @Column(name = "data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao;

    /** ID do veterinário que criou o registro (auditoria) */
    @Column(name = "veterinario_criacao_id")
    private Long veterinarioCriacaoId;

    /** ID do veterinário que atualizou por último (auditoria) */
    @Column(name = "veterinario_atualizacao_id")
    private Long veterinarioAtualizacaoId;

    @Version
    @JsonIgnore
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Long versao;

    @PrePersist
    protected void onCreate() {
        this.dataCriacao = LocalDateTime.now();
        this.dataAtualizacao = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.dataAtualizacao = LocalDateTime.now();
    }
}
