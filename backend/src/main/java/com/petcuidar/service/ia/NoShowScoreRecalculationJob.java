
package com.petcuidar.service.ia;

import com.petcuidar.dto.PredicaoNoShowDTO;
import com.petcuidar.model.Consulta;
import com.petcuidar.model.PredicaoNoShow;
import com.petcuidar.model.StatusConsulta;
import com.petcuidar.repository.ConsultaRepository;
import com.petcuidar.repository.PredicaoNoShowRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class NoShowScoreRecalculationJob {

    private static final Logger log =
            LoggerFactory.getLogger(NoShowScoreRecalculationJob.class);

    private final ConsultaRepository consultaRepository;
    private final PredicaoNoShowRepository predicaoRepository;
    private final NoShowPredictorService predictorService;

    public NoShowScoreRecalculationJob(
            ConsultaRepository consultaRepository,
            PredicaoNoShowRepository predicaoRepository,
            NoShowPredictorService predictorService) {

        this.consultaRepository = consultaRepository;
        this.predicaoRepository = predicaoRepository;
        this.predictorService = predictorService;
    }

    // Executa pela primeira vez após 1 minuto.
    // Depois, executa novamente a cada 1 hora.
    @Scheduled(
        initialDelay = 60000,
        fixedDelay = 3600000
    )
    @Transactional
    public void recalcularScores() {

        LocalDateTime agora = LocalDateTime.now();

        List<Consulta> consultas = consultaRepository.findAll()
                .stream()
                .filter(c -> c.getDataHora() != null)
                .filter(c -> c.getDataHora().isAfter(agora))
                .filter(c ->
                    c.getStatus() == StatusConsulta.AGENDADA ||
                    c.getStatus() == StatusConsulta.CONFIRMADA
                )
                .toList();

        int atualizadas = 0;

        for (Consulta consulta : consultas) {

            PredicaoNoShowDTO resultado =
                    predictorService.calcular(consulta);

            // Evita salvar um score inválido quando a IA estiver indisponível.
            if (resultado.getScore() == null) {
                log.warn(
                    "Consulta {}: score indisponivel, mantendo previsao anterior",
                    consulta.getId()
                );
                continue;
            }

            PredicaoNoShow predicao =
                    predicaoRepository.findByConsultaId(consulta.getId())
                    .orElseGet(PredicaoNoShow::new);

            predicao.setConsulta(consulta);
            predicao.setScoreCru(resultado.getScore());
            predicao.setScoreUsado(resultado.getScore());
            predicao.setMotivo(resultado.getMotivo());
            predicao.setDataCalculo(agora);

            predicaoRepository.save(predicao);

            String categoria = categorizar(resultado.getScore());

            log.info(
                "Consulta {}: score={}, categoria={}",
                consulta.getId(),
                resultado.getScore(),
                categoria
            );

            atualizadas++;
        }

        log.info(
            "Recálculo de predições concluído. Consultas atualizadas: {}",
            atualizadas
        );
    }

    private String categorizar(double score) {

        if (score < 0.25) {
            return "BAIXO";
        }

        if (score < 0.60) {
            return "MEDIO";
        }

        return "ALTO";
    }
}
