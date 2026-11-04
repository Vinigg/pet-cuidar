package com.petcuidar.service;

import com.petcuidar.dto.AtualizacaoAtendimentoRequestDTO;
import com.petcuidar.dto.AtendimentoResponseDTO;
import com.petcuidar.dto.RegistroAtendimentoRequestDTO;
import com.petcuidar.exception.RecursoNaoEncontradoException;
import com.petcuidar.model.*;
import com.petcuidar.repository.ConsultaRepository;
import com.petcuidar.repository.HistoricoAtendimentoRepository;
import com.petcuidar.repository.PetRepository;
import com.petcuidar.repository.VeterinarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Testes do Serviço de Atendimento")
class AtendimentoServiceTest {

    private AtendimentoService atendimentoService;

    @Mock
    private HistoricoAtendimentoRepository historicoRepository;

    @Mock
    private PetRepository petRepository;

    @Mock
    private ConsultaRepository consultaRepository;

    @Mock
    private VeterinarioRepository veterinarioRepository;

    private Pet pet;
    private Consulta consulta;
    private Veterinario veterinario;
    private HistoricoAtendimento atendimento;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        atendimentoService = new AtendimentoService(
                historicoRepository,
                petRepository,
                consultaRepository,
                veterinarioRepository
        );

        // Configurar dados de teste
        pet = new Pet();
        pet.setId(1L);
        pet.setNome("Rex");

        consulta = new Consulta();
        consulta.setId(1L);
        consulta.setPet(pet);

        veterinario = new Veterinario();
        veterinario.setId(1L);
        veterinario.setNome("Dr. João");
        veterinario.setAtivo(true);

        atendimento = new HistoricoAtendimento();
        atendimento.setId(1L);
        atendimento.setPet(pet);
        atendimento.setConsulta(consulta);
        atendimento.setVeterinario(veterinario);
        atendimento.setDescricao("Descrição clínica");
        atendimento.setData(LocalDateTime.now());
    }

    @Test
    @DisplayName("Deve registrar atendimento com sucesso")
    void testRegistrarAtendimentoComSucesso() {
        // Arrange
        RegistroAtendimentoRequestDTO request = new RegistroAtendimentoRequestDTO();
        request.setPetId(1L);
        request.setConsultaId(1L);
        request.setVeterinarioId(1L);
        request.setDescricaoClinica("Pet apresenta sintomas de otite");

        when(petRepository.findById(1L)).thenReturn(Optional.of(pet));
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));
        when(veterinarioRepository.findById(1L)).thenReturn(Optional.of(veterinario));
        when(historicoRepository.save(any(HistoricoAtendimento.class))).thenReturn(atendimento);

        // Act
        AtendimentoResponseDTO response = atendimentoService.registrarAtendimento(request);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Rex", response.getPetNome());
        assertEquals("Dr. João", response.getVeterinarioNome());
        verify(historicoRepository, times(1)).save(any(HistoricoAtendimento.class));
    }

    @Test
    @DisplayName("Deve lançar exceção quando pet não existe")
    void testRegistrarAtendimentoPetNaoEncontrado() {
        // Arrange
        RegistroAtendimentoRequestDTO request = new RegistroAtendimentoRequestDTO();
        request.setPetId(999L);
        request.setConsultaId(1L);
        request.setVeterinarioId(1L);
        request.setDescricaoClinica("Descrição válida");

        when(petRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RecursoNaoEncontradoException.class,
                () -> atendimentoService.registrarAtendimento(request));
        verify(historicoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve lançar exceção quando consulta não existe")
    void testRegistrarAtendimentoConsultaNaoEncontrada() {
        // Arrange
        RegistroAtendimentoRequestDTO request = new RegistroAtendimentoRequestDTO();
        request.setPetId(1L);
        request.setConsultaId(999L);
        request.setVeterinarioId(1L);
        request.setDescricaoClinica("Descrição válida");

        when(petRepository.findById(1L)).thenReturn(Optional.of(pet));
        when(consultaRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RecursoNaoEncontradoException.class,
                () -> atendimentoService.registrarAtendimento(request));
        verify(historicoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve lançar exceção quando veterinário não existe")
    void testRegistrarAtendimentoVeterinarioNaoEncontrado() {
        // Arrange
        RegistroAtendimentoRequestDTO request = new RegistroAtendimentoRequestDTO();
        request.setPetId(1L);
        request.setConsultaId(1L);
        request.setVeterinarioId(999L);
        request.setDescricaoClinica("Descrição válida");

        when(petRepository.findById(1L)).thenReturn(Optional.of(pet));
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));
        when(veterinarioRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RecursoNaoEncontradoException.class,
                () -> atendimentoService.registrarAtendimento(request));
        verify(historicoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve rejeitar veterinário inativo ao registrar")
    void testRegistrarAtendimentoVeterinarioInativo() {
        // Arrange
        RegistroAtendimentoRequestDTO request = new RegistroAtendimentoRequestDTO();
        request.setPetId(1L);
        request.setConsultaId(1L);
        request.setVeterinarioId(1L);
        request.setDescricaoClinica("Descrição válida");

        veterinario.setAtivo(false);

        when(petRepository.findById(1L)).thenReturn(Optional.of(pet));
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));
        when(veterinarioRepository.findById(1L)).thenReturn(Optional.of(veterinario));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> atendimentoService.registrarAtendimento(request));
        verify(historicoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve atualizar atendimento com sucesso")
    void testAtualizarAtendimentoComSucesso() {
        // Arrange
        AtualizacaoAtendimentoRequestDTO request = new AtualizacaoAtendimentoRequestDTO();
        request.setAtendimentoId(1L);
        request.setVeterinarioId(1L);
        request.setDescricaoClinica("Descrição clínica atualizada");

        when(historicoRepository.findById(1L)).thenReturn(Optional.of(atendimento));
        when(veterinarioRepository.findById(1L)).thenReturn(Optional.of(veterinario));
        when(historicoRepository.save(any(HistoricoAtendimento.class))).thenReturn(atendimento);

        // Act
        AtendimentoResponseDTO response = atendimentoService.atualizarAtendimento(request);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getId());
        verify(historicoRepository, times(1)).save(any(HistoricoAtendimento.class));
    }

    @Test
    @DisplayName("Deve lançar exceção ao atualizar atendimento não encontrado")
    void testAtualizarAtendimentoNaoEncontrado() {
        // Arrange
        AtualizacaoAtendimentoRequestDTO request = new AtualizacaoAtendimentoRequestDTO();
        request.setAtendimentoId(999L);
        request.setVeterinarioId(1L);
        request.setDescricaoClinica("Descrição válida");

        when(historicoRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RecursoNaoEncontradoException.class,
                () -> atendimentoService.atualizarAtendimento(request));
        verify(historicoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve rejeitar veterinário inativo ao atualizar")
    void testAtualizarAtendimentoVeterinarioInativo() {
        // Arrange
        AtualizacaoAtendimentoRequestDTO request = new AtualizacaoAtendimentoRequestDTO();
        request.setAtendimentoId(1L);
        request.setVeterinarioId(1L);
        request.setDescricaoClinica("Descrição válida");

        veterinario.setAtivo(false);

        when(historicoRepository.findById(1L)).thenReturn(Optional.of(atendimento));
        when(veterinarioRepository.findById(1L)).thenReturn(Optional.of(veterinario));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> atendimentoService.atualizarAtendimento(request));
        verify(historicoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve obter atendimento com sucesso")
    void testObterAtendimentoComSucesso() {
        // Arrange
        when(historicoRepository.findById(1L)).thenReturn(Optional.of(atendimento));

        // Act
        AtendimentoResponseDTO response = atendimentoService.obterAtendimento(1L);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getId());
    }

    @Test
    @DisplayName("Deve lançar exceção ao obter atendimento não encontrado")
    void testObterAtendimentoNaoEncontrado() {
        // Arrange
        when(historicoRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RecursoNaoEncontradoException.class,
                () -> atendimentoService.obterAtendimento(999L));
    }

    @Test
    @DisplayName("Deve listar atendimentos de um pet com sucesso")
    void testListarAtendimentosPorPetComSucesso() {
        // Arrange
        HistoricoAtendimento atendimento2 = new HistoricoAtendimento();
        atendimento2.setId(2L);
        atendimento2.setPet(pet);
        atendimento2.setDescricao("Segundo atendimento");

        List<HistoricoAtendimento> atendimentos = Arrays.asList(atendimento, atendimento2);
        when(historicoRepository.findByPetIdOrderByDataDesc(1L)).thenReturn(atendimentos);

        // Act
        List<AtendimentoResponseDTO> responses = atendimentoService.listarAtendimentosPorPet(1L);

        // Assert
        assertNotNull(responses);
        assertEquals(2, responses.size());
        verify(historicoRepository, times(1)).findByPetIdOrderByDataDesc(1L);
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando pet não tem atendimentos")
    void testListarAtendimentosPorPetVazio() {
        // Arrange
        when(historicoRepository.findByPetIdOrderByDataDesc(999L)).thenReturn(Arrays.asList());

        // Act
        List<AtendimentoResponseDTO> responses = atendimentoService.listarAtendimentosPorPet(999L);

        // Assert
        assertNotNull(responses);
        assertEquals(0, responses.size());
    }
}
