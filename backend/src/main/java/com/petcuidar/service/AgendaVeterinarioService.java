package com.petcuidar.service;

import com.petcuidar.dto.DisponibilidadeDTO;
import com.petcuidar.exception.RecursoNaoEncontradoException;
import com.petcuidar.model.HorarioAtendimentoVeterinario;
import com.petcuidar.model.StatusConsulta;
import com.petcuidar.model.Veterinario;
import com.petcuidar.repository.ConsultaRepository;
import com.petcuidar.repository.BloqueioAgendaVeterinarioRepository;
import com.petcuidar.repository.HorarioAtendimentoVeterinarioRepository;
import com.petcuidar.repository.VeterinarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AgendaVeterinarioService {
    public static final List<StatusConsulta> STATUS_QUE_LIBERAM_HORARIO =
            List.of(StatusConsulta.CANCELADA, StatusConsulta.NAO_COMPARECEU);

    private final VeterinarioRepository veterinarios;
    private final HorarioAtendimentoVeterinarioRepository horarios;
    private final ConsultaRepository consultas;
    private final BloqueioAgendaVeterinarioRepository bloqueios;
    private final Clock clock;

    public AgendaVeterinarioService(VeterinarioRepository veterinarios,
                                    HorarioAtendimentoVeterinarioRepository horarios,
                                    ConsultaRepository consultas,
                                    BloqueioAgendaVeterinarioRepository bloqueios,
                                    @Value("${app.agenda.time-zone:America/Recife}") String timeZone) {
        this.veterinarios = veterinarios;
        this.horarios = horarios;
        this.consultas = consultas;
        this.bloqueios = bloqueios;
        this.clock = Clock.system(ZoneId.of(timeZone));
    }

    public LocalDateTime agora() {
        return LocalDateTime.now(clock);
    }

    public void validarDataHora(LocalDateTime dataHora) {
        if (dataHora == null || dataHora.isBefore(agora()) || dataHora.getMinute() % 30 != 0
                || dataHora.getSecond() != 0 || dataHora.getNano() != 0) {
            throw new IllegalArgumentException("Informe um horario futuro, em intervalos de 30 minutos.");
        }
    }

    public void validarAtendimento(Veterinario veterinario, LocalDateTime dataHora) {
        validarDataHora(dataHora);
        if (!veterinario.isAtivo() || horarios.findByVeterinarioId(veterinario.getId()).stream()
            .noneMatch(periodo -> periodo.atende(dataHora))
            || bloqueios.existsByVeterinarioIdAndInicioLessThanAndFimGreaterThan(veterinario.getId(),
                dataHora.plusMinutes(HorarioAtendimentoVeterinario.DURACAO_MINUTOS), dataHora)) {
            throw new IllegalArgumentException("Veterinario sem atendimento neste dia e horario.");
        }
    }

    public List<DisponibilidadeDTO> listarDisponibilidade(Long veterinarioId, LocalDate data) {
        Veterinario veterinario = veterinarios.findById(veterinarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veterinario nao encontrado: id " + veterinarioId));
        LocalDateTime agora = agora();
        if (!veterinario.isAtivo() || data.isBefore(agora.toLocalDate())) {
            return List.of();
        }
        Set<LocalDateTime> ocupados = consultas
                .findByVeterinarioIdAndDataHoraBetweenAndStatusNotInOrderByDataHoraAsc(
                        veterinarioId, data.atStartOfDay(), data.atTime(LocalTime.MAX), STATUS_QUE_LIBERAM_HORARIO)
                .stream().map(consulta -> consulta.getDataHora()).collect(Collectors.toSet());
        SortedSet<LocalDateTime> grade = new TreeSet<>();
        var bloqueiosDoDia = bloqueios.findByVeterinarioIdAndInicioLessThanAndFimGreaterThanOrderByInicioAsc(
            veterinarioId, data.plusDays(1).atStartOfDay(), data.atStartOfDay());
        for (HorarioAtendimentoVeterinario periodo : horarios.findByVeterinarioId(veterinarioId)) {
            if (periodo.getDiaSemana() != data.getDayOfWeek()) continue;
            for (LocalDateTime cursor = data.atTime(periodo.getHoraInicio()); periodo.atende(cursor);
                 cursor = cursor.plusMinutes(HorarioAtendimentoVeterinario.DURACAO_MINUTOS)) {
                if (!cursor.isBefore(agora)) grade.add(cursor);
            }
        }
        return grade.stream().map(horario -> new DisponibilidadeDTO(horario, !ocupados.contains(horario)
            && bloqueiosDoDia.stream().noneMatch(bloqueio -> bloqueio.impede(horario)))).toList();
    }
}