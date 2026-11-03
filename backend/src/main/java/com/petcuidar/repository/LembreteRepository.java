package com.petcuidar.repository;

import com.petcuidar.model.Lembrete;
import com.petcuidar.model.StatusEnvioLembrete;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LembreteRepository extends JpaRepository<Lembrete, Long> {
    Optional<Lembrete> findByConsultaId(Long consultaId);
    boolean existsByConsultaIdAndStatusEnvio(Long consultaId, StatusEnvioLembrete status);
}
