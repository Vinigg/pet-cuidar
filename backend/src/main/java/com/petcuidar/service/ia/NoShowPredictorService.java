package com.petcuidar.service.ia;

import com.petcuidar.dto.PredicaoNoShowDTO;
import com.petcuidar.model.Consulta;
import com.petcuidar.model.StatusConsulta;
import com.petcuidar.repository.ConsultaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Predicao de risco de falta (US4.1).
 *
 * Neste projeto de referencia o score e calculado por uma regra simples
 * e explicavel (proporcao de faltas anteriores do tutor), fazendo o papel
 * do "modelo leve (regressao logistica)" descrito na Secao 4.1 do
 * documento de arquitetura. Em uma proxima iteracao, este metodo passaria
 * a chamar um microservico Python com o modelo treinado, mantendo a mesma
 * assinatura (score + motivo) para nao impactar o restante do sistema.
 *
 * Regra de negocio importante (criterio de aceite de IA): o score NUNCA
 * cancela ou reagenda a consulta sozinho - ele e apenas um apoio a decisao
 * da recepcionista, sempre acompanhado de uma explicacao verificavel.
 */
@Service
public class NoShowPredictorService {

    private final ConsultaRepository consultaRepository;

    public NoShowPredictorService(ConsultaRepository consultaRepository) {
        this.consultaRepository = consultaRepository;
    }

    public PredicaoNoShowDTO calcular(Consulta consulta) {
        Long petId = consulta.getPet().getId();

        List<Consulta> historico = consultaRepository.findByPetIdOrderByDataHoraDesc(petId);
        long totalPassadas = historico.stream()
                .filter(c -> !c.getId().equals(consulta.getId()))
                .count();
        long faltas = consultaRepository.findByPetIdAndStatus(petId, StatusConsulta.NAO_COMPARECEU).size();

        double score;
        String motivo;

        if (totalPassadas == 0) {
            score = 0.2; // sem historico - risco basal baixo/medio
            motivo = "Sem historico de consultas anteriores para este pet.";
        } else {
            score = Math.min(1.0, (double) faltas / totalPassadas);
            motivo = String.format("%d falta(s) em %d consulta(s) anteriores.", faltas, totalPassadas);
        }

        String categoria = categorizar(score);
        return new PredicaoNoShowDTO(round(score), categoria, motivo);
    }

    private String categorizar(double score) {
        if (score < 0.25) return "BAIXO";
        if (score < 0.6) return "MEDIO";
        return "ALTO";
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
