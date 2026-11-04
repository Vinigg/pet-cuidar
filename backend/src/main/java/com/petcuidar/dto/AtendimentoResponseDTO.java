package com.petcuidar.dto;

import com.petcuidar.model.HistoricoAtendimento;

import java.time.LocalDateTime;

/**
 * DTO para resposta de atendimento registrado/atualizado.
 * Inclui informações de auditoria e rastreabilidade.
 */
public class AtendimentoResponseDTO {

    private Long id;
    private Long petId;
    private String petNome;
    private Long consultaId;
    private Long veterinarioId;
    private String veterinarioNome;
    private String descricaoClinica;
    private String resumoIa;
    private LocalDateTime dataAtendimento;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;
    private String veterinarioCriacao;
    private String veterinarioAtualizacao;

    public static AtendimentoResponseDTO from(HistoricoAtendimento atendimento) {
        AtendimentoResponseDTO dto = new AtendimentoResponseDTO();
        dto.id = atendimento.getId();
        dto.petId = atendimento.getPet().getId();
        dto.petNome = atendimento.getPet().getNome();
        
        if (atendimento.getConsulta() != null) {
            dto.consultaId = atendimento.getConsulta().getId();
        }
        
        if (atendimento.getVeterinario() != null) {
            dto.veterinarioId = atendimento.getVeterinario().getId();
            dto.veterinarioNome = atendimento.getVeterinario().getNome();
        }
        
        dto.descricaoClinica = atendimento.getDescricao();
        dto.resumoIa = atendimento.getResumoIa();
        dto.dataAtendimento = atendimento.getData();
        dto.dataCriacao = atendimento.getDataCriacao();
        dto.dataAtualizacao = atendimento.getDataAtualizacao();
        
        return dto;
    }

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getPetId() { return petId; }
    public void setPetId(Long petId) { this.petId = petId; }
    
    public String getPetNome() { return petNome; }
    public void setPetNome(String petNome) { this.petNome = petNome; }
    
    public Long getConsultaId() { return consultaId; }
    public void setConsultaId(Long consultaId) { this.consultaId = consultaId; }
    
    public Long getVeterinarioId() { return veterinarioId; }
    public void setVeterinarioId(Long veterinarioId) { this.veterinarioId = veterinarioId; }
    
    public String getVeterinarioNome() { return veterinarioNome; }
    public void setVeterinarioNome(String veterinarioNome) { this.veterinarioNome = veterinarioNome; }
    
    public String getDescricaoClinica() { return descricaoClinica; }
    public void setDescricaoClinica(String descricaoClinica) { this.descricaoClinica = descricaoClinica; }
    
    public String getResumoIa() { return resumoIa; }
    public void setResumoIa(String resumoIa) { this.resumoIa = resumoIa; }
    
    public LocalDateTime getDataAtendimento() { return dataAtendimento; }
    public void setDataAtendimento(LocalDateTime dataAtendimento) { this.dataAtendimento = dataAtendimento; }
    
    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }
    
    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }
    public void setDataAtualizacao(LocalDateTime dataAtualizacao) { this.dataAtualizacao = dataAtualizacao; }
    
    public String getVeterinarioCriacao() { return veterinarioCriacao; }
    public void setVeterinarioCriacao(String veterinarioCriacao) { this.veterinarioCriacao = veterinarioCriacao; }
    
    public String getVeterinarioAtualizacao() { return veterinarioAtualizacao; }
    public void setVeterinarioAtualizacao(String veterinarioAtualizacao) { this.veterinarioAtualizacao = veterinarioAtualizacao; }
}
