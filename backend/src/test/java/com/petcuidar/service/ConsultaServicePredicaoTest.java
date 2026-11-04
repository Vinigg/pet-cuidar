package com.petcuidar.service;

import com.petcuidar.dto.PredicaoNoShowDTO;
import com.petcuidar.model.Consulta;
import com.petcuidar.model.ConsultaStateMachine;
import com.petcuidar.model.PredicaoNoShow;
import com.petcuidar.repository.ConsultaRepository;
import com.petcuidar.repository.LembreteRepository;
import com.petcuidar.repository.PetRepository;
import com.petcuidar.repository.PredicaoNoShowRepository;
import com.petcuidar.repository.VeterinarioRepository;
import com.petcuidar.service.ia.NoShowPredictorService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsultaServicePredicaoTest {

    @Test
    void naoPersisteScoreQuandoPreditorEstaIndisponivel() {
        Contexto contexto = contexto(new PredicaoNoShowDTO(
                null, "INDISPONIVEL", "Score indisponivel no momento"));

        PredicaoNoShowDTO resposta = contexto.service.obterPredicaoNoShow(10L);

        assertNull(resposta.getScore());
        assertNull(resposta.getDataCalculo());
        verify(contexto.predicaoRepository, never()).save(any());
    }

    @Test
    void persistePredicaoValidaEExplicitaDataCalculo() {
        Contexto contexto = contexto(new PredicaoNoShowDTO(
                0.72, "ALTO", "2 faltas em 3 consultas anteriores"));
        when(contexto.predicaoRepository.findByConsultaId(10L)).thenReturn(Optional.empty());
        when(contexto.predicaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PredicaoNoShowDTO resposta = contexto.service.obterPredicaoNoShow(10L);

        assertNotNull(resposta.getDataCalculo());
        verify(contexto.predicaoRepository).save(any(PredicaoNoShow.class));
    }

    private Contexto contexto(PredicaoNoShowDTO resposta) {
        ConsultaRepository consultaRepository = mock(ConsultaRepository.class);
        PetRepository petRepository = mock(PetRepository.class);
        VeterinarioRepository veterinarioRepository = mock(VeterinarioRepository.class);
        PredicaoNoShowRepository predicaoRepository = mock(PredicaoNoShowRepository.class);
        NoShowPredictorService predictor = mock(NoShowPredictorService.class);
        ConsultaStateMachine stateMachine = mock(ConsultaStateMachine.class);
        LembreteDeliveryService lembreteDeliveryService = mock(LembreteDeliveryService.class);
        Consulta consulta = new Consulta();
        consulta.setId(10L);
        when(consultaRepository.findById(10L)).thenReturn(Optional.of(consulta));
        when(predictor.calcular(consulta)).thenReturn(resposta);
        ConsultaService service = new ConsultaService(
                consultaRepository,
                petRepository,
                veterinarioRepository,
                predicaoRepository,
                mock(LembreteRepository.class),
                predictor,
                stateMachine,
                lembreteDeliveryService,
                mock(AgendaVeterinarioService.class));
        return new Contexto(service, predicaoRepository);
    }

    private record Contexto(
            ConsultaService service,
            PredicaoNoShowRepository predicaoRepository) {}
}
