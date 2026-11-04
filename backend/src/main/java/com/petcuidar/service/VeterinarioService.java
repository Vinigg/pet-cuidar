package com.petcuidar.service;

import com.petcuidar.dto.*;
import com.petcuidar.exception.ConflitoHorarioException;
import com.petcuidar.exception.RecursoNaoEncontradoException;
import com.petcuidar.model.HorarioAtendimentoVeterinario;
import com.petcuidar.model.Veterinario;
import com.petcuidar.model.BloqueioAgendaVeterinario;
import com.petcuidar.model.Consulta;
import com.petcuidar.repository.BloqueioAgendaVeterinarioRepository;
import com.petcuidar.repository.ConsultaRepository;
import com.petcuidar.repository.HorarioAtendimentoVeterinarioRepository;
import com.petcuidar.repository.VeterinarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class VeterinarioService {
    private final VeterinarioRepository veterinarios;
    private final HorarioAtendimentoVeterinarioRepository horarios;
    private final ConsultaRepository consultas;
    private final AgendaVeterinarioService agenda;
    private final BloqueioAgendaVeterinarioRepository bloqueios;

    public VeterinarioService(VeterinarioRepository veterinarios, HorarioAtendimentoVeterinarioRepository horarios,
                              ConsultaRepository consultas, AgendaVeterinarioService agenda,
                              BloqueioAgendaVeterinarioRepository bloqueios) {
        this.veterinarios = veterinarios;
        this.horarios = horarios;
        this.consultas = consultas;
        this.agenda = agenda;
        this.bloqueios = bloqueios;
    }

    public List<VeterinarioResponseDTO> listar(boolean ativo) {
        return veterinarios.findByAtivoOrderByNomeAsc(ativo).stream().map(VeterinarioResponseDTO::from).toList();
    }

    public VeterinarioResponseDTO obter(Long id) {
        return VeterinarioResponseDTO.from(buscar(id));
    }

    public List<HorarioAtendimentoDTO> listarHorarios(Long id) {
        buscar(id);
        return horarios.findByVeterinarioId(id).stream()
                .sorted(Comparator.comparing(HorarioAtendimentoVeterinario::getDiaSemana)
                        .thenComparing(HorarioAtendimentoVeterinario::getHoraInicio))
                .map(HorarioAtendimentoDTO::from).toList();
    }

    @Transactional
    public VeterinarioResponseDTO cadastrar(VeterinarioCadastroDTO dto) {
        Veterinario veterinario = new Veterinario();
        veterinario.setNome(dto.nome().trim());
        veterinario.setEspecialidade(dto.especialidade());
        List<HorarioAtendimentoVeterinario> escala = construirEscala(veterinario,
                dto.horarios() == null ? List.of() : dto.horarios());
        veterinarios.save(veterinario);
        horarios.saveAll(escala);
        return VeterinarioResponseDTO.from(veterinario);
    }

    @Transactional
    public VeterinarioResponseDTO atualizar(Long id, VeterinarioAtualizacaoDTO dto) {
        Veterinario veterinario = bloquear(id);
        if (!dto.ativo() && !consultasFuturas(id).isEmpty()) {
            throw new ConflitoHorarioException("Reagende ou cancele as consultas futuras antes de inativar o veterinario.");
        }
        veterinario.setNome(dto.nome().trim());
        veterinario.setEspecialidade(dto.especialidade());
        veterinario.setAtivo(dto.ativo());
        return VeterinarioResponseDTO.from(veterinarios.save(veterinario));
    }

    @Transactional
    public List<HorarioAtendimentoDTO> substituirHorarios(Long id, EscalaVeterinarioDTO dto) {
        Veterinario veterinario = bloquear(id);
        List<HorarioAtendimentoVeterinario> escala = construirEscala(veterinario, dto.horarios());
        if (consultasFuturas(id).stream().anyMatch(consulta -> escala.stream()
                .noneMatch(periodo -> periodo.atende(consulta.getDataHora())))) {
            throw new ConflitoHorarioException("A nova escala deixa consultas futuras fora do atendimento.");
        }
        horarios.deleteAll(horarios.findByVeterinarioId(id));
        horarios.saveAll(escala);
        return escala.stream().map(HorarioAtendimentoDTO::from).toList();
    }

    public List<BloqueioAgendaResponseDTO> listarBloqueios(Long id, LocalDate inicio, LocalDate fim) {
        buscar(id);
        if (fim.isBefore(inicio) || inicio.plusYears(1).isBefore(fim)) {
            throw new IllegalArgumentException("Informe um periodo de ate um ano, com inicio anterior ao fim.");
        }
        return bloqueios.findByVeterinarioIdAndInicioLessThanAndFimGreaterThanOrderByInicioAsc(
                id, fim.plusDays(1).atStartOfDay(), inicio.atStartOfDay())
                .stream().map(BloqueioAgendaResponseDTO::from).toList();
    }

    @Transactional
    public BloqueioAgendaResponseDTO criarBloqueio(Long id, BloqueioAgendaRequestDTO dto) {
        Veterinario veterinario = bloquear(id);
        BloqueioAgendaVeterinario bloqueio = new BloqueioAgendaVeterinario(
                veterinario, dto.inicio(), dto.fim(), dto.motivo());
        if (!dto.fim().isAfter(agenda.agora())) {
            throw new IllegalArgumentException("O bloqueio deve terminar no futuro.");
        }
        if (bloqueios.existsByVeterinarioIdAndInicioLessThanAndFimGreaterThan(id, dto.fim(), dto.inicio())) {
            throw new ConflitoHorarioException("Ja existe bloqueio neste periodo.");
        }
        LocalDateTime inicioBusca = agenda.agora().minusMinutes(HorarioAtendimentoVeterinario.DURACAO_MINUTOS);
        if (consultas.findByVeterinarioIdAndDataHoraGreaterThanEqualAndStatusNotIn(
                id, inicioBusca, AgendaVeterinarioService.STATUS_QUE_LIBERAM_HORARIO)
                .stream().anyMatch(consulta -> bloqueio.impede(consulta.getDataHora()))) {
            throw new ConflitoHorarioException("Reagende ou cancele as consultas afetadas antes de bloquear o periodo.");
        }
        return BloqueioAgendaResponseDTO.from(bloqueios.save(bloqueio));
    }

    @Transactional
    public void removerBloqueio(Long id, Long bloqueioId) {
        bloquear(id);
        BloqueioAgendaVeterinario bloqueio = bloqueios.findByIdAndVeterinarioId(bloqueioId, id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Bloqueio nao encontrado: id " + bloqueioId));
        bloqueios.delete(bloqueio);
    }

    private List<Consulta> consultasFuturas(Long id) {
        return consultas.findByVeterinarioIdAndDataHoraGreaterThanEqualAndStatusNotIn(
                id, agenda.agora(), AgendaVeterinarioService.STATUS_QUE_LIBERAM_HORARIO);
    }

    private List<HorarioAtendimentoVeterinario> construirEscala(Veterinario veterinario,
                                                               List<HorarioAtendimentoDTO> periodos) {
        List<HorarioAtendimentoVeterinario> escala = periodos.stream()
                .map(periodo -> new HorarioAtendimentoVeterinario(veterinario, periodo.diaSemana(),
                        periodo.horaInicio(), periodo.horaFim()))
                .sorted(Comparator.comparing(HorarioAtendimentoVeterinario::getDiaSemana)
                        .thenComparing(HorarioAtendimentoVeterinario::getHoraInicio)).toList();
        for (int indice = 1; indice < escala.size(); indice++) {
            var anterior = escala.get(indice - 1);
            var atual = escala.get(indice);
            if (anterior.getDiaSemana() == atual.getDiaSemana()
                    && atual.getHoraInicio().isBefore(anterior.getHoraFim())) {
                throw new IllegalArgumentException("Os periodos de atendimento nao podem se sobrepor.");
            }
        }
        return escala;
    }

    private Veterinario buscar(Long id) {
        return veterinarios.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veterinario nao encontrado: id " + id));
    }

    private Veterinario bloquear(Long id) {
        return veterinarios.buscarParaAgendamento(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veterinario nao encontrado: id " + id));
    }
}