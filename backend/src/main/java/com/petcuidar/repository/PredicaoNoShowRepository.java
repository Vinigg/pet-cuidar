package com.petcuidar.repository;

import com.petcuidar.model.PredicaoNoShow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PredicaoNoShowRepository extends JpaRepository<PredicaoNoShow, Long> {
    Optional<PredicaoNoShow> findByConsultaId(Long consultaId);
}
