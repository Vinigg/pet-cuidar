package com.petcuidar.repository;

import com.petcuidar.model.HistoricoAtendimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoricoAtendimentoRepository extends JpaRepository<HistoricoAtendimento, Long> {
    List<HistoricoAtendimento> findByPetIdOrderByDataDesc(Long petId);

    List<HistoricoAtendimento> findByPetIdAndConsultaVeterinarioIdOrderByDataDesc(Long petId, Long veterinarioId);
}
