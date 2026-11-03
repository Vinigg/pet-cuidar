package com.petcuidar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcuidar.dto.PetCadastroDTO;
import com.petcuidar.dto.TutorCadastroDTO;
import com.petcuidar.model.Tutor;
import com.petcuidar.repository.TutorRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TutorServiceTest {

    @Test
    void retornaDadosSalvosSemReferenciaCircular() throws Exception {
        TutorRepository repository = mock(TutorRepository.class);
        when(repository.saveAndFlush(any(Tutor.class))).thenAnswer(invocation -> {
            Tutor tutor = invocation.getArgument(0);
            assertEquals(2, tutor.getPets().size());
            tutor.setId(10L);
            for (int i = 0; i < tutor.getPets().size(); i++) {
                assertSame(tutor, tutor.getPets().get(i).getTutor());
                tutor.getPets().get(i).setId(20L + i);
            }
            return tutor;
        });

        PetCadastroDTO rex = new PetCadastroDTO();
        rex.setNome("Rex");
        rex.setEspecie("Cachorro");
        rex.setIdade(3);
        rex.setAlergiasCriticas("Dipirona");
        PetCadastroDTO luna = new PetCadastroDTO();
        luna.setNome("Luna");

        TutorCadastroDTO entrada = new TutorCadastroDTO();
        entrada.setNome("Arthur");
        entrada.setEmail("arthur@example.com");
        entrada.setTelefone("85999999999");
        entrada.setCpf("12345678900");
        entrada.setPets(List.of(rex, luna));

        var resposta = new TutorService(repository).cadastrar(entrada);

        assertEquals(10L, resposta.id());
        assertEquals("Arthur", resposta.nome());
        assertEquals("arthur@example.com", resposta.email());
        assertEquals("85999999999", resposta.telefone());
        assertEquals(2, resposta.pets().size());
        assertEquals(20L, resposta.pets().get(0).id());
        assertEquals("Rex", resposta.pets().get(0).nome());
        assertEquals("Cachorro", resposta.pets().get(0).especie());
        assertEquals(3, resposta.pets().get(0).idade());
        assertEquals("Dipirona", resposta.pets().get(0).alergiasCriticas());
        assertEquals(21L, resposta.pets().get(1).id());
        assertEquals("Luna", resposta.pets().get(1).nome());

        var json = new ObjectMapper().valueToTree(resposta);
        assertFalse(json.has("cpf"));
        assertFalse(json.get("pets").get(0).has("tutor"));
        verify(repository).saveAndFlush(any(Tutor.class));
    }

    @Test
    void traduzConflitoDoBancoMesmoSeConsultaPreviaNaoDetectarDuplicidade() {
        TutorRepository repository = mock(TutorRepository.class);
        when(repository.saveAndFlush(any(Tutor.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("unique"));
        PetCadastroDTO pet = new PetCadastroDTO();
        pet.setNome("Rex");
        TutorCadastroDTO entrada = new TutorCadastroDTO();
        entrada.setNome("Arthur");
        entrada.setEmail("arthur@example.com");
        entrada.setPets(List.of(pet));

        var erro = assertThrows(com.petcuidar.exception.CadastroConflitanteException.class,
                () -> new TutorService(repository).cadastrar(entrada));
        assertEquals("Cadastro em conflito com os dados existentes.", erro.getMessage());
        assertInstanceOf(org.springframework.dao.DataIntegrityViolationException.class, erro.getCause());
    }
}