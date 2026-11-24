package com.petcuidar.repository;

import com.petcuidar.model.Pet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.petcuidar.dto.PetOpcaoDTO;

import java.util.List;

public interface PetRepository extends JpaRepository<Pet, Long> {
	@Query("select new com.petcuidar.dto.PetOpcaoDTO(p.id, p.nome, p.especie, t.id, t.nome) "
			+ "from Pet p join p.tutor t where (:tutorId is null or t.id = :tutorId) "
			+ "order by lower(p.nome), lower(t.nome), p.id")
	List<PetOpcaoDTO> listarOpcoes(@Param("tutorId") Long tutorId);

    List<Pet> findByTutorIdOrderByNomeAsc(Long tutorId);

    @Query("""
            select new com.petcuidar.dto.PetOpcaoDTO(p.id, p.nome, p.especie, p.tutor.id, p.tutor.nome) from Pet p
            where exists (select 1 from Consulta c where c.pet = p and c.veterinario.id = :veterinarioId)
            order by lower(p.nome), p.id
            """)
    List<PetOpcaoDTO> listarOpcoesVeterinario(@Param("veterinarioId") Long veterinarioId);
}
