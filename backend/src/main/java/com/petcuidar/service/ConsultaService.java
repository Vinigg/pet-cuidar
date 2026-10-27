package com.petcuidar.service;

import com.petcuidar.dto.*;
import com.petcuidar.exception.ConflitoHorarioException;
import com.petcuidar.exception.RecursoNaoEncontradoException;
import com.petcuidar.model.*;
import com.petcuidar.repository.*;
import com.petcuidar.service.ia.LembreteGeneratorService;
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

    private final ConsultaRepository consultaRepository;
    private final PetRepository petRepository;
    private final VeterinarioRepository veterinarioRepository;
    private final LembreteRepository lembreteRepository;
    private final PredicaoNoShowRepository predicaoRepository;
    private final LembreteGeneratorService lembreteGeneratorService;
    private final NoShowPredictorService noShowPredictorService;
    private final ConsultaStateMachine consultaStateMachine;

    public ConsultaService(ConsultaRepository consultaRepository,
                            PetRepository petRepository,
                            VeterinarioRepository veterinarioRepository,
                            LembreteRepository lembreteRepository,
                            PredicaoNoShowRepository predicaoRepository,
                            LembreteGeneratorService lembreteGeneratorService,
                            NoShowPredictorService noShowPredictorService,
                            ConsultaStateMachine consultaStateMachine) {
        this.consultaRepository = consultaRepository;
        this.petRepository = petRepository;
        this.veterinarioRepository = veterinarioRepository;
        this.lembreteRepository = lembreteRepository;
        this.predicaoRepository = predicaoRepository;
        this.lembreteGeneratorService = lembreteGeneratorService;
        this.noShowPredictorService = noShowPredictorService;
        this.consultaStateMachine = consultaStateMachine;
    }

    // GET /veterinarios/{id}/disponibilidade
    public List<DisponibilidadeDTO> listarDisponibilidade(Long veterinarioId, LocalDate data) {
        veterinarioRepository.findById(veterinarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veterinario nao encontrado: id " + veterinarioId));

        List<Consulta> doDia = consultaRepository.findByDataHoraBetweenOrderByDataHoraAsc(
                data.atTime(ABERTURA), data.atTime(FECHAMENTO));

        List<LocalDateTime> ocupados = doDia.stream()
                .filter(c -> c.getVeterinario().getId().equals(veterinarioId))
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
        Pet pet = petRepository.findById(dto.getPetId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pet nao encontrado: id " + dto.getPetId()));
        Veterinario veterinario = veterinarioRepository.findById(dto.getVeterinarioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veterinario nao encontrado: id " + dto.getVeterinarioId()));

        boolean conflito = !consultaRepository
                .findByVeterinarioIdAndDataHora(dto.getVeterinarioId(), dto.getDataHora())
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

        PredicaoNoShow entidade = predicaoRepository.findByConsultaId(consultaId).orElseGet(PredicaoNoShow::new);
        entidade.setConsulta(consulta);
        entidade.setScore(dto.getScore());
        entidade.setMotivo(dto.getMotivo());
        entidade.setDataCalculo(LocalDateTime.now());
        predicaoRepository.save(entidade);

        return dto;
    }

    // POST /consultas/{id}/lembrete (US3.1)
    @Transactional
    public LembreteResponseDTO dispararLembrete(Long consultaId) {
        Consulta consulta = buscarConsulta(consultaId);

        LembreteGeneratorService.ResultadoLembrete resultado = lembreteGeneratorService.gerar(consulta);

        Lembrete lembrete = lembreteRepository.findByConsultaId(consultaId).orElseGet(Lembrete::new);
        lembrete.setConsulta(consulta);
        lembrete.setTextoGeradoIa(resultado.textoGeradoIa);
        lembrete.setTextoValidado(resultado.textoValidado);
        lembrete.setUsouFallback(resultado.usouFallback);
        lembrete.setStatusEnvio(StatusEnvioLembrete.ENVIADO);
        lembrete.setDataEnvio(LocalDateTime.now());

        return LembreteResponseDTO.from(lembreteRepository.save(lembrete));
    }

    private Consulta buscarConsulta(Long id) {
        return consultaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Consulta nao encontrada: id " + id));
    }
}
