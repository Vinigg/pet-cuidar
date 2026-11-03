package com.petcuidar.service;

import com.petcuidar.model.Consulta;
import com.petcuidar.model.StatusConsulta;
import com.petcuidar.model.StatusEnvioLembrete;
import com.petcuidar.repository.ConsultaRepository;
import com.petcuidar.repository.LembreteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
@ConditionalOnProperty(name = "lembrete.job.enabled", havingValue = "true")
public class LembreteScheduler {
    private static final Logger log = LoggerFactory.getLogger(LembreteScheduler.class);
    private static final List<StatusConsulta> ELEGIVEIS =
            List.of(StatusConsulta.AGENDADA, StatusConsulta.CONFIRMADA);

    private final ConsultaRepository consultaRepository;
    private final LembreteRepository lembreteRepository;
    private final LembreteDeliveryService deliveryService;
    private final long antecedenciaHoras;
    private final long janelaMinutos;
    private final ZoneId zoneId;

    @Autowired
    public LembreteScheduler(ConsultaRepository consultaRepository,
                             LembreteRepository lembreteRepository,
                             LembreteDeliveryService deliveryService,
                             @Value("${lembrete.job.antecedencia-horas:24}") long antecedenciaHoras,
                             @Value("${lembrete.job.janela-minutos:5}") long janelaMinutos,
                             @Value("${lembrete.job.time-zone:America/Recife}") String timeZone) {
        this.consultaRepository = consultaRepository;
        this.lembreteRepository = lembreteRepository;
        this.deliveryService = deliveryService;
        this.antecedenciaHoras = antecedenciaHoras;
        this.janelaMinutos = janelaMinutos;
        this.zoneId = ZoneId.of(timeZone);
    }

    LembreteScheduler(ConsultaRepository consultaRepository,
                      LembreteRepository lembreteRepository,
                      LembreteDeliveryService deliveryService,
                      long antecedenciaHoras,
                      long janelaMinutos) {
        this(consultaRepository, lembreteRepository, deliveryService,
                antecedenciaHoras, janelaMinutos, "America/Recife");
    }

    @Scheduled(fixedDelayString = "${lembrete.job.interval-ms:60000}",
            initialDelayString = "${lembrete.job.initial-delay-ms:10000}")
    public void processar() {
        processarEm(LocalDateTime.now(zoneId));
    }

    void processarEm(LocalDateTime agora) {
        // Inclui consultas que entraram na janela durante indisponibilidade ou
        // reinicio. A idempotencia impede reenvio das ja processadas.
        LocalDateTime inicio = agora;
        LocalDateTime fim = agora.plusHours(antecedenciaHoras).plusMinutes(janelaMinutos);
        List<Consulta> consultas = consultaRepository
                .findByDataHoraBetweenAndStatusInOrderByDataHoraAsc(inicio, fim, ELEGIVEIS);

        for (Consulta consulta : consultas) {
            if (lembreteRepository.existsByConsultaIdAndStatusEnvio(
                    consulta.getId(), StatusEnvioLembrete.ENVIADO)) {
                continue;
            }
            try {
                deliveryService.disparar(consulta.getId());
            } catch (RuntimeException e) {
                log.error("Falha ao processar lembrete automatico da consulta {}: {}",
                        consulta.getId(), e.getClass().getSimpleName());
            }
        }
    }
}
