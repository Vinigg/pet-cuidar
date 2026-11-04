package com.petcuidar.repository;

import com.petcuidar.model.Veterinario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface VeterinarioRepository extends JpaRepository<Veterinario, Long> {

    List<Veterinario> findByAtivoOrderByNomeAsc(boolean ativo);

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select v from Veterinario v where v.id = :id")
    Optional<Veterinario> buscarParaAgendamento(@Param("id") Long id);
}