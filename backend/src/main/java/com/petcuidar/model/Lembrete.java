package com.petcuidar.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Guarda separadamente o texto BRUTO gerado pela IA (texto_gerado_ia) e o
 * texto EFETIVAMENTE enviado apos o guardrail (texto_validado) - que pode
 * ser igual ao gerado ou igual ao template fixo de fallback. Essa separacao
 * e o que permite auditar quando a IA "errou" sem nunca expor o erro ao tutor.
 */
@Entity
@Table(name = "lembretes")
@Getter
@Setter
@NoArgsConstructor
public class Lembrete {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "consulta_id", nullable = false, unique = true)
    private Consulta consulta;

    private String canal = "EMAIL";

    @Column(name = "texto_gerado_ia", length = 1000)
    private String textoGeradoIa;

    @Column(name = "texto_validado", length = 1000)
    private String textoValidado;

    @Enumerated(EnumType.STRING)
    private StatusEnvioLembrete statusEnvio = StatusEnvioLembrete.PENDENTE;

    private LocalDateTime dataEnvio;

    /** true quando o texto da IA falhou no guardrail e o fallback foi usado. */
    private boolean usouFallback;
}
