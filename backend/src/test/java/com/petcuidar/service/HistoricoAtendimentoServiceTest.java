package com.petcuidar.service;

import com.petcuidar.dto.HistoricoPetDTO;
import com.petcuidar.dto.ResumoHistoricoDTO;
import com.petcuidar.exception.RecursoNaoEncontradoException;
import com.petcuidar.model.*;
import com.petcuidar.repository.ConsultaRepository;
import com.petcuidar.repository.HistoricoAtendimentoRepository;
import com.petcuidar.repository.PetRepository;
import com.petcuidar.service.ia.ResumoHistoricoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("HistoricoAtendimentoService - prontuario do pet")
class HistoricoAtendimentoServiceTest {

    private PetRepository petRepository;
    private HistoricoAtendimentoRepository historicoRepository;
    private ConsultaRepository consultaRepository;
    private ResumoHistoricoService resumoHistoricoService;
    private HistoricoAtendimentoService service;

    private Pet pet;
    private Veterinario vet;

    @BeforeEach
    void setUp() {
        petRepository = mock(PetRepository.class);
        historicoRepository = mock(HistoricoAtendimentoRepository.class);
        consultaRepository = mock(ConsultaRepository.class);
        resumoHistoricoService = mock(ResumoHistoricoService.class);
        service = new HistoricoAtendimentoService(petRepository, historicoRepository, consultaRepository,
                resumoHistoricoService);

        Tutor tutor = new Tutor();
        tutor.setNome("Joao");

        pet = new Pet();
        pet.setId(2L);
        pet.setNome("Bob");
        pet.setEspecie("Pitbull");
        pet.setIdade(4);
        pet.setTutor(tutor);

        vet = new Veterinario();
        vet.setId(3L);
        vet.setNome("Alberto Siqueira");
    }

    private Consulta consulta(Long id, LocalDateTime dataHora) {
        Consulta c = new Consulta();
        c.setId(id);
        c.setPet(pet);
        c.setVeterinario(vet);
        c.setDataHora(dataHora);
        return c;
    }

    private HistoricoAtendimento registro(Long id, Consulta consulta, String descricao) {
        HistoricoAtendimento h = new HistoricoAtendimento();
        h.setId(id);
        h.setPet(pet);
        h.setConsulta(consulta);
        h.setVeterinario(vet);
        h.setDescricao(descricao);
        h.setData(LocalDateTime.now());
        return h;
    }

    @Test
    @DisplayName("Agrupa atendimentos por consulta, mantendo ordem das consultas e registros avulsos")
    void agrupaAtendimentosPorConsulta() {
        Consulta recente = consulta(10L, LocalDateTime.of(2026, 11, 4, 17, 30));
        Consulta antiga = consulta(9L, LocalDateTime.of(2026, 10, 1, 9, 0));
        when(petRepository.findById(2L)).thenReturn(Optional.of(pet));
        when(consultaRepository.findByPetIdOrderByDataHoraDesc(2L)).thenReturn(List.of(recente, antiga));
        when(historicoRepository.findByPetIdOrderByDataDesc(2L)).thenReturn(List.of(
                registro(100L, recente, "Retorno sem alteracoes"),
                registro(101L, null, "Registro importado sem consulta")));

        HistoricoPetDTO dto = service.obterHistorico(2L);

        assertEquals("Bob", dto.petNome());
        assertEquals("Pitbull", dto.especie());
        assertEquals("Joao", dto.tutorNome());
        assertEquals(2, dto.consultas().size());
        assertEquals(10L, dto.consultas().get(0).consultaId());
        assertEquals("Alberto Siqueira", dto.consultas().get(0).veterinarioNome());
        assertEquals(StatusConsulta.AGENDADA, dto.consultas().get(0).status());
        assertEquals(1, dto.consultas().get(0).atendimentos().size());
        assertEquals("Retorno sem alteracoes", dto.consultas().get(0).atendimentos().get(0).getDescricaoClinica());
        assertTrue(dto.consultas().get(1).atendimentos().isEmpty());
        assertEquals(1, dto.atendimentosSemConsulta().size());
        assertEquals(101L, dto.atendimentosSemConsulta().get(0).getId());
    }

    @Test
    @DisplayName("Itens criticos vem do cadastro do pet, sem depender da IA")
    void itensCriticosVemDoCadastro() {
        pet.setAlergiasCriticas("Alergia a dipirona");
        when(petRepository.findById(2L)).thenReturn(Optional.of(pet));
        when(consultaRepository.findByPetIdOrderByDataHoraDesc(2L)).thenReturn(List.of());
        when(historicoRepository.findByPetIdOrderByDataDesc(2L)).thenReturn(List.of());

        HistoricoPetDTO dto = service.obterHistorico(2L);

        assertEquals("Alergia a dipirona", dto.itensCriticos());
        assertTrue(dto.consultas().isEmpty());
        assertTrue(dto.atendimentosSemConsulta().isEmpty());
    }

    @Test
    @DisplayName("Itens criticos em branco sao retornados como null")
    void itensCriticosEmBrancoViramNull() {
        pet.setAlergiasCriticas("   ");
        when(petRepository.findById(2L)).thenReturn(Optional.of(pet));
        when(consultaRepository.findByPetIdOrderByDataHoraDesc(2L)).thenReturn(List.of());
        when(historicoRepository.findByPetIdOrderByDataDesc(2L)).thenReturn(List.of());

        assertNull(service.obterHistorico(2L).itensCriticos());
    }

    @Test
    @DisplayName("Pet inexistente nao retorna dados clinicos")
    void petInexistente() {
        when(petRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> service.obterHistorico(99L));
        verifyNoInteractions(historicoRepository, consultaRepository);
    }

    @Test
    @DisplayName("Resumo usa o historico do pet e delega a geracao ao ResumoHistoricoService")
    void resumoDelegaGeracao() {
        List<HistoricoAtendimento> registros = List.of(registro(100L, null, "Vacina anual"));
        ResumoHistoricoDTO esperado = new ResumoHistoricoDTO("Resumo", "", "/pets/2/historico", false);
        when(petRepository.findById(2L)).thenReturn(Optional.of(pet));
        when(historicoRepository.findByPetIdOrderByDataDesc(2L)).thenReturn(registros);
        when(resumoHistoricoService.gerar(pet, registros)).thenReturn(esperado);

        assertSame(esperado, service.obterResumo(2L));
    }

    @Test
    @DisplayName("Resumo de pet inexistente nao chama a IA")
    void resumoPetInexistente() {
        when(petRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> service.obterResumo(99L));
        verifyNoInteractions(historicoRepository, resumoHistoricoService);
    }
}
