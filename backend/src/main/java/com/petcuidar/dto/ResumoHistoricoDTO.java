package com.petcuidar.dto;

public class ResumoHistoricoDTO {
    private String resumo;
    private String itensCriticos;
    private String linkHistoricoCompleto;
    private boolean usouFallback;

    public ResumoHistoricoDTO(String resumo, String itensCriticos,
                              String linkHistoricoCompleto, boolean usouFallback) {
        this.resumo = resumo;
        this.itensCriticos = itensCriticos;
        this.linkHistoricoCompleto = linkHistoricoCompleto;
        this.usouFallback = usouFallback;
    }

    public String getResumo() { return resumo; }
    public String getItensCriticos() { return itensCriticos; }
    public String getLinkHistoricoCompleto() { return linkHistoricoCompleto; }
    public boolean isUsouFallback() { return usouFallback; }
}
