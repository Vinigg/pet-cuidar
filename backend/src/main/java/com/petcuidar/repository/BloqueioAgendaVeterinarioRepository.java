package com.petcuidar.repository;

import com.petcuidar.model.BloqueioAgendaVeterinario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BloqueioAgendaVeterinarioRepository extends JpaRepository<BloqueioAgendaVeterinario, Long> {
    List<BloqueioAgendaVeterinario> findByVeterinarioIdAndInicioLessThanAndFimGreaterThanOrderByInicioAsc(
            Long veterinarioId, LocalDateTime fim, LocalDateTime inicio);

    boolean existsByVeterinarioIdAndInicioLessThanAndFimGreaterThan(
            Long veterinarioId, LocalDateTime fim, LocalDateTime inicio);

    Optional<BloqueioAgendaVeterinario> findByIdAndVeterinarioId(Long id, Long veterinarioId);
}