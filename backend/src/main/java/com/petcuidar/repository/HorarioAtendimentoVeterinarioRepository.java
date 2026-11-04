package com.petcuidar.repository;

import com.petcuidar.model.HorarioAtendimentoVeterinario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HorarioAtendimentoVeterinarioRepository extends JpaRepository<HorarioAtendimentoVeterinario, Long> {
    List<HorarioAtendimentoVeterinario> findByVeterinarioId(Long veterinarioId);
}