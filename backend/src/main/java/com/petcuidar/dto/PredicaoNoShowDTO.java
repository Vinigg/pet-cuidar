package com.petcuidar.dto;

public class PredicaoNoShowDTO {
    private double score;
    private String categoria; // BAIXO, MEDIO, ALTO
    private String motivo;

    public PredicaoNoShowDTO(double score, String categoria, String motivo) {
        this.score = score;
        this.categoria = categoria;
        this.motivo = motivo;
    }

    public double getScore() { return score; }
    public String getCategoria() { return categoria; }
    public String getMotivo() { return motivo; }
}
