package com.petcuidar.repository;

import com.petcuidar.model.Pet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.petcuidar.dto.PetOpcaoDTO;

import java.util.List;

public interface PetRepository extends JpaRepository<Pet, Long> {
	@Query("select new com.petcuidar.dto.PetOpcaoDTO(p.id, p.nome, p.especie, t.nome) "
			+ "from Pet p join p.tutor t where (:tutorId is null or t.id = :tutorId) "
			+ "order by lower(p.nome), lower(t.nome), p.id")
	List<PetOpcaoDTO> listarOpcoes(@Param("tutorId") Long tutorId);
}
