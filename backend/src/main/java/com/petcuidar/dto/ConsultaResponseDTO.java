package com.petcuidar.dto;

import com.petcuidar.model.Consulta;
import com.petcuidar.model.StatusConsulta;

import java.time.LocalDateTime;

public class ConsultaResponseDTO {
    private Long id;
    private String petNome;
    private String tutorNome;
    private String veterinarioNome;
    private LocalDateTime dataHora;
    private StatusConsulta status;

    public static ConsultaResponseDTO from(Consulta c) {
        ConsultaResponseDTO dto = new ConsultaResponseDTO();
        dto.id = c.getId();
        dto.petNome = c.getPet().getNome();
        dto.tutorNome = c.getPet().getTutor().getNome();
        dto.veterinarioNome = c.getVeterinario().getNome();
        dto.dataHora = c.getDataHora();
        dto.status = c.getStatus();
        return dto;
    }

    public Long getId() { return id; }
    public String getPetNome() { return petNome; }
    public String getTutorNome() { return tutorNome; }
    public String getVeterinarioNome() { return veterinarioNome; }
    public LocalDateTime getDataHora() { return dataHora; }
    public StatusConsulta getStatus() { return status; }
}
