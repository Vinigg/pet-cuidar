package com.petcuidar.service;

import com.petcuidar.dto.*;
import com.petcuidar.exception.ConflitoHorarioException;
import com.petcuidar.exception.RecursoNaoEncontradoException;
import com.petcuidar.model.*;
import com.petcuidar.repository.*;
import com.petcuidar.service.ia.NoShowPredictorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ConsultaService {

    private static final LocalTime ABERTURA = LocalTime.of(8, 0);
    private static final LocalTime FECHAMENTO = LocalTime.of(18, 0);
    private static final int INTERVALO_MINUTOS = 30;
    private static final List<StatusConsulta> STATUS_QUE_LIBERAM_HORARIO =
            List.of(StatusConsulta.CANCELADA, StatusConsulta.NAO_COMPARECEU);

    private final ConsultaRepository consultaRepository;
    private final PetRepository petRepository;
    private final VeterinarioRepository veterinarioRepository;
    private final PredicaoNoShowRepository predicaoRepository;
    private final NoShowPredictorService noShowPredictorService;
    private final ConsultaStateMachine consultaStateMachine;
    private final LembreteDeliveryService lembreteDeliveryService;

    public ConsultaService(ConsultaRepository consultaRepository,
                            PetRepository petRepository,
                            VeterinarioRepository veterinarioRepository,
                            PredicaoNoShowRepository predicaoRepository,
                            NoShowPredictorService noShowPredictorService,
                            ConsultaStateMachine consultaStateMachine,
                            LembreteDeliveryService lembreteDeliveryService) {
        this.consultaRepository = consultaRepository;
        this.petRepository = petRepository;
        this.veterinarioRepository = veterinarioRepository;
        this.predicaoRepository = predicaoRepository;
        this.noShowPredictorService = noShowPredictorService;
        this.consultaStateMachine = consultaStateMachine;
        this.lembreteDeliveryService = lembreteDeliveryService;
    }

    // GET /veterinarios/{id}/disponibilidade
    public List<DisponibilidadeDTO> listarDisponibilidade(Long veterinarioId, LocalDate data) {
        veterinarioRepository.findById(veterinarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veterinario nao encontrado: id " + veterinarioId));

        List<LocalDateTime> ocupados = consultaRepository
                .findByVeterinarioIdAndDataHoraBetweenAndStatusNotInOrderByDataHoraAsc(
                        veterinarioId, data.atTime(ABERTURA), data.atTime(FECHAMENTO), STATUS_QUE_LIBERAM_HORARIO)
                .stream()
                .map(Consulta::getDataHora)
                .collect(Collectors.toList());

        List<DisponibilidadeDTO> horarios = new ArrayList<>();
        LocalTime cursor = ABERTURA;
        while (cursor.isBefore(FECHAMENTO)) {
            LocalDateTime horario = data.atTime(cursor);
            horarios.add(new DisponibilidadeDTO(horario, !ocupados.contains(horario)));
            cursor = cursor.plusMinutes(INTERVALO_MINUTOS);
        }
        return horarios;
    }

    // POST /consultas (US2.1) - impede dois agendamentos no mesmo horario/veterinario
    @Transactional
    public ConsultaResponseDTO criar(ConsultaRequestDTO dto) {
        validarHorario(dto.getDataHora());

        Pet pet = petRepository.findById(dto.getPetId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pet nao encontrado: id " + dto.getPetId()));
        Veterinario veterinario = veterinarioRepository.buscarParaAgendamento(dto.getVeterinarioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veterinario nao encontrado: id " + dto.getVeterinarioId()));

        boolean conflito = !consultaRepository
                .findByVeterinarioIdAndDataHoraAndStatusNotIn(
                        dto.getVeterinarioId(), dto.getDataHora(), STATUS_QUE_LIBERAM_HORARIO)
                .isEmpty();
        if (conflito) {
            throw new ConflitoHorarioException(
                    "Ja existe uma consulta marcada para este veterinario neste horario.");
        }

        Consulta consulta = new Consulta();
        consulta.setPet(pet);
        consulta.setVeterinario(veterinario);
        consulta.setDataHora(dto.getDataHora());
        return ConsultaResponseDTO.from(consultaRepository.save(consulta));
    }

    // GET /consultas?data= (US2.2 - painel diario da recepcionista)
    public List<ConsultaResponseDTO> listarDoDia(LocalDate data) {
        return consultaRepository
                .findByDataHoraBetweenOrderByDataHoraAsc(data.atStartOfDay(), data.atTime(LocalTime.MAX))
                .stream()
                .map(ConsultaResponseDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public ConsultaResponseDTO atualizarStatus(Long consultaId, StatusConsulta status) {
        Consulta consulta = buscarConsulta(consultaId);
        consultaStateMachine.transicionar(consulta, status);
        return ConsultaResponseDTO.from(consultaRepository.save(consulta));
    }

    // GET /consultas/{id}/predicao-no-show (US4.1)
    @Transactional
    public PredicaoNoShowDTO obterPredicaoNoShow(Long consultaId) {
        Consulta consulta = buscarConsulta(consultaId);
        PredicaoNoShowDTO dto = noShowPredictorService.calcular(consulta);

        if (dto.getScore() == null || "INDISPONIVEL".equals(dto.getCategoria())) {
            return dto;
        }

        PredicaoNoShow entidade = predicaoRepository.findByConsultaId(consultaId).orElseGet(PredicaoNoShow::new);
        LocalDateTime dataCalculo = LocalDateTime.now();
        entidade.setConsulta(consulta);
        entidade.setScore(dto.getScore());
        entidade.setMotivo(dto.getMotivo());
        entidade.setDataCalculo(dataCalculo);
        predicaoRepository.save(entidade);

        return new PredicaoNoShowDTO(
                dto.getScore(), dto.getCategoria(), dto.getMotivo(), dataCalculo);
    }

    // POST /consultas/{id}/lembrete (US3.1)
    public LembreteResponseDTO dispararLembrete(Long consultaId) {
        return lembreteDeliveryService.disparar(consultaId);
    }

    private void validarHorario(LocalDateTime dataHora) {
        LocalTime hora = dataHora.toLocalTime();
        boolean foraDoExpediente = hora.isBefore(ABERTURA) || !hora.isBefore(FECHAMENTO);
        boolean foraDoIntervalo = hora.getMinute() % INTERVALO_MINUTOS != 0
                || hora.getSecond() != 0 || hora.getNano() != 0;
        if (foraDoExpediente || foraDoIntervalo) {
            throw new IllegalArgumentException(
                    "Horario invalido: use horarios de 30 em 30 minutos entre 08:00 e 17:30.");
        }
        if (dataHora.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Nao e possivel agendar uma consulta no passado.");
        }
    }

    private Consulta buscarConsulta(Long id) {
        return consultaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Consulta nao encontrada: id " + id));
    }
}
