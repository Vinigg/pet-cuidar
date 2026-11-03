package com.petcuidar.service.ia;

import com.petcuidar.dto.PredicaoNoShowDTO;
import com.petcuidar.model.Consulta;
import com.petcuidar.model.ConsultaStateMachine;
import com.petcuidar.model.Pet;
import com.petcuidar.model.StatusConsulta;
import com.petcuidar.model.Tutor;
import com.petcuidar.repository.ConsultaRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NoShowPredictorServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Recife");
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 11, 3, 12, 0).atZone(ZONE).toInstant(), ZONE);

    @Test
    void enviaContratoCompletoComHistoricoDisponivelERetornaPredicao() {
        ConsultaRepository repository = mock(ConsultaRepository.class);
        Consulta atual = consulta(10L, LocalDateTime.of(2026, 11, 20, 18, 0), 10);
        Consulta realizada = consulta(1L, LocalDateTime.of(2026, 10, 1, 9, 0), 10);
        Consulta falta = consulta(2L, LocalDateTime.of(2026, 10, 15, 14, 0), 10);
        Consulta futura = consulta(3L, LocalDateTime.of(2026, 11, 10, 14, 0), 10);
        alterarStatus(realizada, StatusConsulta.REALIZADA);
        alterarStatus(falta, StatusConsulta.NAO_COMPARECEU);
        when(repository.findByPetTutorIdOrderByDataHoraDesc(5L))
                .thenReturn(List.of(atual, futura, falta, realizada));

        RestClient.Builder builder = RestClient.builder().baseUrl("http://predictor.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://predictor.test/predict"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "consultas_anteriores": 2,
                          "faltas_anteriores": 1,
                          "dias_antecedencia": 17,
                          "periodo": 2,
                          "ja_confirmou_antes": 0,
                          "idade_pet": 10
                        }
                        """, true))
                .andRespond(withSuccess("""
                        {"score":0.86,"categoria":"ALTO","motivo":"1 falta em 2 consultas anteriores."}
                        """, MediaType.APPLICATION_JSON));

        NoShowPredictorService service = new NoShowPredictorService(repository, builder.build(), CLOCK);
        PredicaoNoShowDTO result = service.calcular(atual);

        assertEquals(0.86, result.getScore());
        assertEquals("ALTO", result.getCategoria());
        assertEquals("1 falta em 2 consultas anteriores.", result.getMotivo());
        assertEquals(StatusConsulta.AGENDADA, atual.getStatus());
        server.verify();
    }

    @Test
    void erroHttpRetornaIndisponivelSemAlterarConsulta() {
        ConsultaRepository repository = mock(ConsultaRepository.class);
        Consulta atual = consulta(10L, LocalDateTime.of(2026, 11, 20, 10, 0), null);
        when(repository.findByPetTutorIdOrderByDataHoraDesc(5L)).thenReturn(List.of());

        RestClient.Builder builder = RestClient.builder().baseUrl("http://predictor.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://predictor.test/predict")).andRespond(withServerError());

        PredicaoNoShowDTO result = new NoShowPredictorService(repository, builder.build(), CLOCK).calcular(atual);

        assertIndisponivel(result);
        assertEquals(StatusConsulta.AGENDADA, atual.getStatus());
        server.verify();
    }

    @Test
    void respostaForaDoContratoRetornaIndisponivel() {
        ConsultaRepository repository = mock(ConsultaRepository.class);
        Consulta atual = consulta(10L, LocalDateTime.of(2026, 11, 20, 14, 0), 4);
        when(repository.findByPetTutorIdOrderByDataHoraDesc(5L)).thenReturn(List.of());

        RestClient.Builder builder = RestClient.builder().baseUrl("http://predictor.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://predictor.test/predict"))
                .andRespond(withSuccess(
                        "{\"score\":1.5,\"categoria\":\"ALTO\",\"motivo\":\"invalido\"}",
                        MediaType.APPLICATION_JSON));

        PredicaoNoShowDTO result = new NoShowPredictorService(repository, builder.build(), CLOCK).calcular(atual);

        assertIndisponivel(result);
        server.verify();
    }

    @Test
    void respostaSemScoreRetornaIndisponivel() {
        ConsultaRepository repository = mock(ConsultaRepository.class);
        Consulta atual = consulta(10L, LocalDateTime.of(2026, 11, 20, 14, 0), 4);
        when(repository.findByPetTutorIdOrderByDataHoraDesc(5L)).thenReturn(List.of());

        RestClient.Builder builder = RestClient.builder().baseUrl("http://predictor.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://predictor.test/predict"))
                .andRespond(withSuccess(
                        "{\"categoria\":\"BAIXO\",\"motivo\":\"campo ausente\"}",
                        MediaType.APPLICATION_JSON));

        PredicaoNoShowDTO result = new NoShowPredictorService(repository, builder.build(), CLOCK).calcular(atual);

        assertIndisponivel(result);
        server.verify();
    }

    @Test
    void timeoutConfiguradoInterrompeEsperaERetornaIndisponivel() throws Exception {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/predict", exchange -> {
            try {
                Thread.sleep(500);
                byte[] body = "{\"score\":0.2,\"categoria\":\"BAIXO\",\"motivo\":\"tarde\"}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        httpServer.start();

        try {
            ConsultaRepository repository = mock(ConsultaRepository.class);
            Consulta atual = consulta(10L, LocalDateTime.of(2030, 1, 1, 10, 0), 4);
            when(repository.findByPetTutorIdOrderByDataHoraDesc(5L)).thenReturn(List.of());
            NoShowPredictorService service = new NoShowPredictorService(
                    repository,
                    RestClient.builder(),
                    "http://localhost:" + httpServer.getAddress().getPort(),
                    30);

            long inicio = System.nanoTime();
            PredicaoNoShowDTO result = service.calcular(atual);
            long duracaoMs = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

            assertIndisponivel(result);
            assertTrue(duracaoMs < 450, "o timeout configurado deve limitar a espera");
            assertEquals(StatusConsulta.AGENDADA, atual.getStatus());
        } finally {
            httpServer.stop(0);
        }
    }

    @Test
    void falhaAoLerHistoricoTambemNaoPropagaNemAlteraConsulta() {
        ConsultaRepository repository = mock(ConsultaRepository.class);
        Consulta atual = consulta(10L, LocalDateTime.of(2026, 11, 20, 10, 0), 4);
        when(repository.findByPetTutorIdOrderByDataHoraDesc(5L))
                .thenThrow(new IllegalStateException("banco indisponivel"));

        NoShowPredictorService service = new NoShowPredictorService(
                repository, RestClient.create("http://predictor.test"), CLOCK);
        PredicaoNoShowDTO result = service.calcular(atual);

        assertIndisponivel(result);
        assertEquals(StatusConsulta.AGENDADA, atual.getStatus());
    }

    private static Consulta consulta(Long id, LocalDateTime dataHora, Integer idadePet) {
        Tutor tutor = new Tutor();
        tutor.setId(5L);
        Pet pet = new Pet();
        pet.setId(7L);
        pet.setTutor(tutor);
        pet.setNome("Rex");
        pet.setIdade(idadePet);
        Consulta consulta = new Consulta();
        consulta.setId(id);
        consulta.setPet(pet);
        consulta.setDataHora(dataHora);
        return consulta;
    }

    private static void alterarStatus(Consulta consulta, StatusConsulta status) {
        new ConsultaStateMachine(CLOCK, Duration.ZERO).transicionar(consulta, status);
    }

    private static void assertIndisponivel(PredicaoNoShowDTO result) {
        assertNull(result.getScore());
        assertEquals("INDISPONIVEL", result.getCategoria());
        assertTrue(result.getMotivo().contains("Score indisponivel"));
    }
}
