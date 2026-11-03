package com.petcuidar.dto;

public class ResumoHistoricoDTO {
    private String resumo;
    private String linkHistoricoCompleto;

    public ResumoHistoricoDTO(String resumo, String linkHistoricoCompleto) {
        this.resumo = resumo;
        this.linkHistoricoCompleto = linkHistoricoCompleto;
    }

    public String getResumo() { return resumo; }
    public String getLinkHistoricoCompleto() { return linkHistoricoCompleto; }
}
