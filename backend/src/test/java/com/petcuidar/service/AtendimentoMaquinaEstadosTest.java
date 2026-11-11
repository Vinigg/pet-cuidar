package com.petcuidar.service;

import com.petcuidar.dto.RegistroAtendimentoRequestDTO;
import com.petcuidar.exception.TransicaoEstadoInvalidaException;
import com.petcuidar.model.*;
import com.petcuidar.repository.ConsultaRepository;
import com.petcuidar.repository.HistoricoAtendimentoRepository;
import com.petcuidar.repository.PetRepository;
import com.petcuidar.repository.VeterinarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Registro de atendimento integrado a ConsultaStateMachine. */
class AtendimentoMaquinaEstadosTest {

    private static final ZoneId ZONA = ZoneId.of("America/Recife");
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 11, 20, 12, 0).atZone(ZONA).toInstant(), ZONA);

    private final HistoricoAtendimentoRepository historicos = mock(HistoricoAtendimentoRepository.class);
    private final PetRepository pets = mock(PetRepository.class);
    private final ConsultaRepository consultas = mock(ConsultaRepository.class);
    private final VeterinarioRepository veterinarios = mock(VeterinarioRepository.class);
    private final ConsultaStateMachine maquina = new ConsultaStateMachine(CLOCK, Duration.ZERO);
    private final AtendimentoService service =
            new AtendimentoService(historicos, pets, consultas, veterinarios, maquina);

    private Pet pet;
    private Consulta consulta;

    @BeforeEach
    void setUp() {
        pet = new Pet(); pet.setId(1L); pet.setNome("Rex");
        Veterinario vet = new Veterinario(); vet.setId(3L); vet.setNome("Dra. Bia"); vet.setAtivo(true);
        consulta = new Consulta(); consulta.setId(2L); consulta.setPet(pet); consulta.setVeterinario(vet);
        consulta.setDataHora(LocalDateTime.of(2026, 11, 20, 10, 0));

        when(pets.findById(1L)).thenReturn(Optional.of(pet));
        when(consultas.findById(2L)).thenReturn(Optional.of(consulta));
        when(veterinarios.findById(3L)).thenReturn(Optional.of(vet));
        when(historicos.save(any(HistoricoAtendimento.class))).thenAnswer(inv -> {
            HistoricoAtendimento h = inv.getArgument(0); h.setId(9L); return h;
        });
    }

    @Test
    void registrarAtendimentoDeConsultaAgendadaMarcaComoRealizada() {
        service.registrarAtendimento(request());

        assertEquals(StatusConsulta.REALIZADA, consulta.getStatus());
        verify(consultas).save(consulta);
        verify(historicos).save(any(HistoricoAtendimento.class));
    }

    @Test
    void registrarAtendimentoDeConsultaConfirmadaMarcaComoRealizada() {
        maquina.transicionar(consulta, StatusConsulta.CONFIRMADA);

        service.registrarAtendimento(request());

        assertEquals(StatusConsulta.REALIZADA, consulta.getStatus());
    }

    @Test
    void consultaJaRealizadaAceitaRegistroAdicionalSemNovaTransicao() {
        maquina.transicionar(consulta, StatusConsulta.REALIZADA);

        service.registrarAtendimento(request());

        assertEquals(StatusConsulta.REALIZADA, consulta.getStatus());
        verify(consultas, never()).save(any());
        verify(historicos).save(any(HistoricoAtendimento.class));
    }

    @ParameterizedTest
    @EnumSource(value = StatusConsulta.class, names = {"CANCELADA", "NAO_COMPARECEU"})
    void consultaEncerradaSemAtendimentoRecusaRegistroCom409(StatusConsulta status) {
        Clock antes = Clock.fixed(LocalDateTime.of(2026, 11, 20, 9, 0).atZone(ZONA).toInstant(), ZONA);
        ConsultaStateMachine maquinaDoStatus = status == StatusConsulta.CANCELADA
                ? new ConsultaStateMachine(antes, Duration.ZERO) : maquina;
        maquinaDoStatus.transicionar(consulta, status);

        TransicaoEstadoInvalidaException erro = assertThrows(TransicaoEstadoInvalidaException.class,
                () -> service.registrarAtendimento(request()));

        assertTrue(erro.getMessage().contains(status.name()));
        assertEquals(status, consulta.getStatus());
        verify(historicos, never()).save(any());
        verify(consultas, never()).save(any());
    }

    @Test
    void consultaDeOutroPetERecusadaCom400() {
        Pet outro = new Pet(); outro.setId(99L);
        consulta.setPet(outro);

        assertThrows(IllegalArgumentException.class, () -> service.registrarAtendimento(request()));

        assertEquals(StatusConsulta.AGENDADA, consulta.getStatus());
        verify(historicos, never()).save(any());
    }

    @Test
    void veterinarioInativoNaoAlteraStatus() {
        Veterinario inativo = new Veterinario(); inativo.setId(3L); inativo.setAtivo(false);
        when(veterinarios.findById(3L)).thenReturn(Optional.of(inativo));

        assertThrows(IllegalArgumentException.class, () -> service.registrarAtendimento(request()));

        assertEquals(StatusConsulta.AGENDADA, consulta.getStatus());
    }

    private static RegistroAtendimentoRequestDTO request() {
        RegistroAtendimentoRequestDTO request = new RegistroAtendimentoRequestDTO();
        request.setPetId(1L);
        request.setConsultaId(2L);
        request.setVeterinarioId(3L);
        request.setDescricaoClinica("Otite leve, prescrito tratamento topico.");
        return request;
    }
}
