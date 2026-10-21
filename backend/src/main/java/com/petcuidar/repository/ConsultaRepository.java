package com.petcuidar.repository;

import com.petcuidar.model.Consulta;
import com.petcuidar.model.StatusConsulta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {

    // Usado para checar conflito de horario (US2.1: mesmo veterinario, mesmo horario)
    List<Consulta> findByVeterinarioIdAndDataHora(Long veterinarioId, LocalDateTime dataHora);

    // Usado pelo painel diario da recepcionista (US2.2)
    List<Consulta> findByDataHoraBetweenOrderByDataHoraAsc(LocalDateTime inicio, LocalDateTime fim);

    // Usado para estimar o score de no-show a partir do historico do pet
    List<Consulta> findByPetIdAndStatus(Long petId, StatusConsulta status);

    List<Consulta> findByPetIdOrderByDataHoraDesc(Long petId);
}
