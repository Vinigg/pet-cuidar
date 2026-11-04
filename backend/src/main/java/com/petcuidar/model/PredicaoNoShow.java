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

    /**
     * Score CRU: exatamente o que o modelo/regra de predicao devolveu, sem
     * nenhum tratamento. Fica guardado so para auditoria e nunca e exibido.
     * Nulo quando o preditor esta indisponivel (timeout/circuit breaker).
     */
    @Column(name = "score_cru", precision = 4, scale = 3)
    private Double scoreCru;

    /**
     * Score USADO: o valor validado (limitado a 0.0-1.0) que a recepcionista
     * ve e que o restante do sistema consome. Nulo quando nao ha predicao
     * ("score indisponivel"). 0.0 = baixo risco, 1.0 = alto risco.
     */
    @Column(name = "score_usado", precision = 4, scale = 3)
    private Double scoreUsado;

    /** true quando o score usado veio do fallback e nao do preditor. */
    @Column(name = "usou_fallback", nullable = false)
    private boolean usouFallback;

    /** Explicacao curta e verificavel do motivo do score (nunca so um numero). */
    @Column(name = "motivo", length = 500)
    private String motivo;

    @Column(name = "data_calculo", nullable = false)
    private LocalDateTime dataCalculo;
}
