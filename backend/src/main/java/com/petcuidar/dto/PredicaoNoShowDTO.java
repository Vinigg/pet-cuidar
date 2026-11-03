package com.petcuidar.dto;

import java.time.LocalDateTime;

public class PredicaoNoShowDTO {
    private Double score;
    private String categoria; // BAIXO, MEDIO, ALTO ou INDISPONIVEL
    private String motivo;
    private LocalDateTime dataCalculo;

    public PredicaoNoShowDTO(Double score, String categoria, String motivo) {
        this(score, categoria, motivo, null);
    }

    public PredicaoNoShowDTO(Double score, String categoria, String motivo,
                             LocalDateTime dataCalculo) {
        this.score = score;
        this.categoria = categoria;
        this.motivo = motivo;
        this.dataCalculo = dataCalculo;
    }

    public Double getScore() { return score; }
    public String getCategoria() { return categoria; }
    public String getMotivo() { return motivo; }
    public LocalDateTime getDataCalculo() { return dataCalculo; }
}
