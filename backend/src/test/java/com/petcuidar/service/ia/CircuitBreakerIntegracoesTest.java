package com.petcuidar.service.ia;

import com.petcuidar.config.IaProperties;
import com.petcuidar.dto.PredicaoNoShowDTO;
import com.petcuidar.dto.ResumoHistoricoDTO;
import com.petcuidar.model.*;
import com.petcuidar.repository.ConsultaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

/**
 * Cada integracao de IA tem o seu circuit breaker: com o circuito aberto o
 * fallback e usado direto, sem chamar o servico externo, e a falha de uma
 * integracao nao abre o circuito das outras.
 */
class CircuitBreakerIntegracoesTest {

    private static final ZoneId ZONA = ZoneId.of("America/Recife");
    private final CircuitBreakerIaTest.RelogioManual relogio = new CircuitBreakerIaTest.RelogioManual();

    @Test
    void resumoComCircuitoAbertoUsaFallbackSemChamarProvedor() {
        AtomicInteger chamadas = new AtomicInteger();
        IaProperties props = new IaProperties();
        props.getResumo().getCircuitBreaker().setMaxFalhas(2);
        props.getResumo().getCircuitBreaker().setResetMs(1000L);
        ResumoHistoricoService service = new ResumoHistoricoService((nome, atendimentos, criticos) -> {
            chamadas.incrementAndGet();
            throw new IllegalStateException("offline");
        }, props, relogio);
        try {
            Pet pet = pet();
            service.gerar(pet, List.of());
            service.gerar(pet, List.of());
            assertEquals(CircuitBreakerIa.Estado.ABERTO, service.obterMetricas().estado());

            ResumoHistoricoDTO resultado = service.gerar(pet, List.of());
            assertTrue(resultado.isUsouFallback());
            assertTrue(resultado.getResumo().contains("Amendoim"), "itens criticos continuam no fallback");
            assertEquals(2, chamadas.get(), "circuito aberto nao chama o provedor");

            relogio.avancar(1000);
            service.gerar(pet, List.of());
            assertEquals(3, chamadas.get(), "apos o reset uma chamada de prova sai");
        } finally {
            service.encerrarExecutor();
        }
    }

    @Test
    void resumoComSucessoMantemCircuitoFechado() {
        ResumoHistoricoService service = new ResumoHistoricoService(
                (nome, atendimentos, criticos) -> "Resumo gerado", new IaProperties(), relogio);
        try {
            ResumoHistoricoDTO resultado = service.gerar(pet(), List.of());
            assertFalse(resultado.isUsouFallback());
            assertEquals(CircuitBreakerIa.Estado.FECHADO, service.obterMetricas().estado());
            assertEquals(1, service.obterMetricas().sucessos());
        } finally {
            service.encerrarExecutor();
        }
    }

    @Test
    void predicaoComCircuitoAbertoRetornaScoreIndisponivelSemChamarMicroservico() {
        ConsultaRepository repository = mock(ConsultaRepository.class);
        Consulta consulta = consulta();
        when(repository.findByPetTutorIdOrderByDataHoraDesc(5L)).thenReturn(List.of());

        RestClient.Builder builder = RestClient.builder().baseUrl("http://predictor.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(times(2), requestTo("http://predictor.test/predict")).andRespond(withServerError());

        Clock clock = Clock.fixed(LocalDateTime.of(2026, 11, 3, 12, 0).atZone(ZONA).toInstant(), ZONA);
        NoShowPredictorService service = new NoShowPredictorService(repository, builder.build(), clock,
                new CircuitBreakerIa("predicao-no-show", 2, 30_000, relogio));

        service.calcular(consulta);
        service.calcular(consulta);
        assertEquals(CircuitBreakerIa.Estado.ABERTO, service.obterMetricas().estado());

        PredicaoNoShowDTO resultado = service.calcular(consulta);
        assertNull(resultado.getScore());
        assertEquals("INDISPONIVEL", resultado.getCategoria());
        assertEquals(StatusConsulta.AGENDADA, consulta.getStatus());
        server.verify(); // exatamente 2 chamadas: a terceira nao saiu
    }

    @Test
    void resumoFechaCircuitoQuandoAChamadaDeTesteTemSucesso() {
        java.util.concurrent.atomic.AtomicBoolean online = new java.util.concurrent.atomic.AtomicBoolean(false);
        ResumoHistoricoService service = new ResumoHistoricoService((nome, atendimentos, criticos) -> {
            if (!online.get()) throw new IllegalStateException("offline");
            return "Resumo gerado";
        }, propsResumo(2, 1000), relogio);
        try {
            service.gerar(pet(), List.of());
            service.gerar(pet(), List.of());
            assertEquals(CircuitBreakerIa.Estado.ABERTO, service.obterMetricas().estado());

            relogio.avancar(1000);
            online.set(true);
            ResumoHistoricoDTO resultado = service.gerar(pet(), List.of());

            assertFalse(resultado.isUsouFallback());
            assertEquals(CircuitBreakerIa.Estado.FECHADO, service.obterMetricas().estado());
            assertEquals(0, service.obterMetricas().falhasConsecutivas());
        } finally {
            service.encerrarExecutor();
        }
    }

    @Test
    void resumoReabreCircuitoQuandoAChamadaDeTesteFalha() {
        AtomicInteger chamadas = new AtomicInteger();
        ResumoHistoricoService service = new ResumoHistoricoService((nome, atendimentos, criticos) -> {
            chamadas.incrementAndGet();
            return " "; // resposta vazia conta como falha
        }, propsResumo(1, 1000), relogio);
        try {
            service.gerar(pet(), List.of());
            relogio.avancar(1000);
            assertTrue(service.gerar(pet(), List.of()).isUsouFallback());
            assertEquals(CircuitBreakerIa.Estado.ABERTO, service.obterMetricas().estado());

            service.gerar(pet(), List.of());
            assertEquals(2, chamadas.get(), "reaberto, nao chama o provedor ate o proximo reset");
        } finally {
            service.encerrarExecutor();
        }
    }

    @Test
    void predicaoFechaCircuitoQuandoAChamadaDeTesteTemSucesso() {
        Fixture f = new Fixture();
        f.server.expect(times(2), requestTo("http://predictor.test/predict")).andRespond(withServerError());
        f.server.expect(requestTo("http://predictor.test/predict")).andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"score\":0.2,\"categoria\":\"BAIXO\",\"motivo\":\"Sem faltas.\"}",
                        org.springframework.http.MediaType.APPLICATION_JSON));

        f.service.calcular(f.consulta);
        f.service.calcular(f.consulta);
        assertEquals(CircuitBreakerIa.Estado.ABERTO, f.service.obterMetricas().estado());

        relogio.avancar(30_000);
        PredicaoNoShowDTO resultado = f.service.calcular(f.consulta);

        assertEquals(0.2, resultado.getScore());
        assertEquals(CircuitBreakerIa.Estado.FECHADO, f.service.obterMetricas().estado());
        f.server.verify();
    }

    @Test
    void predicaoReabreCircuitoQuandoAChamadaDeTesteFalha() {
        Fixture f = new Fixture();
        f.server.expect(times(3), requestTo("http://predictor.test/predict")).andRespond(withServerError());

        f.service.calcular(f.consulta);
        f.service.calcular(f.consulta);
        relogio.avancar(30_000);
        assertEquals("INDISPONIVEL", f.service.calcular(f.consulta).getCategoria());
        assertEquals(CircuitBreakerIa.Estado.ABERTO, f.service.obterMetricas().estado());

        assertEquals("INDISPONIVEL", f.service.calcular(f.consulta).getCategoria());
        f.server.verify(); // a quarta chamada nao saiu
    }

    private IaProperties propsResumo(int maxFalhas, long resetMs) {
        IaProperties props = new IaProperties();
        props.getResumo().getCircuitBreaker().setMaxFalhas(maxFalhas);
        props.getResumo().getCircuitBreaker().setResetMs(resetMs);
        return props;
    }

    /** Preditor com MockRestServiceServer e circuit breaker (2 falhas, reset 30 s) no relogio do teste. */
    private class Fixture {
        final ConsultaRepository repository = mock(ConsultaRepository.class);
        final Consulta consulta = consulta();
        final MockRestServiceServer server;
        final NoShowPredictorService service;

        Fixture() {
            when(repository.findByPetTutorIdOrderByDataHoraDesc(5L)).thenReturn(List.of());
            RestClient.Builder builder = RestClient.builder().baseUrl("http://predictor.test");
            server = MockRestServiceServer.bindTo(builder).build();
            Clock clock = Clock.fixed(LocalDateTime.of(2026, 11, 3, 12, 0).atZone(ZONA).toInstant(), ZONA);
            service = new NoShowPredictorService(repository, builder.build(), clock,
                    new CircuitBreakerIa("predicao-no-show", 2, 30_000, relogio));
        }
    }

    @Test
    void lembreteRespeitaLimitesProprios() {
        AtomicInteger chamadas = new AtomicInteger();
        IaProperties props = new IaProperties();
        props.getLembrete().getCircuitBreaker().setMaxFalhas(1);
        LembreteGeneratorService service = new LembreteGeneratorService(props, dados -> {
            chamadas.incrementAndGet();
            throw new IllegalStateException("offline");
        });
        try {
            service.gerar(consulta());
            assertEquals(LembreteGeneratorService.EstadoCircuito.ABERTO, service.obterMetricas().estado());
            assertTrue(service.gerar(consulta()).usouFallback);
            assertEquals(1, chamadas.get());
        } finally {
            service.encerrarExecutor();
        }
    }

    @Test
    void circuitosDasIntegracoesSaoIndependentes() {
        IaProperties props = new IaProperties();
        props.getCircuitBreaker().setMaxFalhas(1);
        LembreteGeneratorService lembrete = new LembreteGeneratorService(props, dados -> {
            throw new IllegalStateException("offline");
        });
        ResumoHistoricoService resumo = new ResumoHistoricoService(
                (nome, atendimentos, criticos) -> "Resumo gerado", props, relogio);
        try {
            lembrete.gerar(consulta());
            assertEquals(LembreteGeneratorService.EstadoCircuito.ABERTO, lembrete.obterMetricas().estado());
            assertFalse(resumo.gerar(pet(), List.of()).isUsouFallback());
            assertEquals(CircuitBreakerIa.Estado.FECHADO, resumo.obterMetricas().estado());
        } finally {
            lembrete.encerrarExecutor();
            resumo.encerrarExecutor();
        }
    }

    private static Pet pet() {
        Tutor tutor = new Tutor(); tutor.setId(5L); tutor.setNome("Ana");
        Pet pet = new Pet(); pet.setId(7L); pet.setNome("Rex"); pet.setTutor(tutor);
        pet.setAlergiasCriticas("Amendoim");
        return pet;
    }

    private static Consulta consulta() {
        Consulta consulta = new Consulta();
        consulta.setId(10L);
        consulta.setPet(pet());
        consulta.setDataHora(LocalDateTime.of(2026, 11, 20, 10, 0));
        return consulta;
    }
}
