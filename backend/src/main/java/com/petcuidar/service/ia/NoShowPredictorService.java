package com.petcuidar.service.ia;

import com.petcuidar.config.IaProperties;
import com.petcuidar.dto.PredicaoNoShowDTO;
import com.petcuidar.model.Consulta;
import com.petcuidar.model.StatusConsulta;
import com.petcuidar.repository.ConsultaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Cliente do microservico Python de predicao de no-show (US4.1).
 *
 * <p>A predicao e somente apoio a decisao: este servico nao altera o estado
 * da consulta. Quando o microservico nao responde ou devolve uma resposta
 * invalida, o DTO deixa isso explicito em vez de transformar uma heuristica
 * local em uma probabilidade aparentemente produzida pelo modelo.</p>
 *
 * <p>Resiliencia: timeout (ia.predicao.timeout-ms, variavel
 * NOSHOW_PREDICTOR_TIMEOUT_MS) e um {@link CircuitBreakerIa} proprio
 * (ia.predicao.circuit-breaker.*). Com o circuito aberto, a resposta e
 * "score indisponivel" sem chamar o microservico.</p>
 */
@Service
public class NoShowPredictorService {

    private static final Logger log = LoggerFactory.getLogger(NoShowPredictorService.class);
    private static final Set<String> CATEGORIAS_VALIDAS = Set.of("BAIXO", "MEDIO", "ALTO");
    private static final String CATEGORIA_INDISPONIVEL = "INDISPONIVEL";
    private static final String MOTIVO_INDISPONIVEL =
            "Score indisponivel no momento; a consulta nao foi alterada.";

    private final ConsultaRepository consultaRepository;
    private final RestClient restClient;
    private final Clock clock;
    private final CircuitBreakerIa circuitBreaker;

    @Autowired
    public NoShowPredictorService(
            ConsultaRepository consultaRepository,
            RestClient.Builder restClientBuilder,
            @Value("${noshow.predictor.base-url:http://localhost:8000}") String baseUrl,
            IaProperties props) {
        this(consultaRepository,
                criarCliente(restClientBuilder, baseUrl, props.timeoutMs(props.getPredicao())),
                Clock.systemDefaultZone(),
                new CircuitBreakerIa("predicao-no-show", props.circuitBreaker(props.getPredicao()),
                        Clock.systemUTC()));
    }

    /** Timeout explicito e circuit breaker com os limites padrao. */
    public NoShowPredictorService(ConsultaRepository consultaRepository, RestClient.Builder restClientBuilder,
                                  String baseUrl, long timeoutMs) {
        this(consultaRepository, criarCliente(restClientBuilder, baseUrl, timeoutMs), Clock.systemDefaultZone(),
                new CircuitBreakerIa("predicao-no-show", new IaProperties.CircuitBreaker(), Clock.systemUTC()));
    }

    /** Para testes: circuit breaker com os limites padrao. */
    NoShowPredictorService(ConsultaRepository consultaRepository, RestClient restClient, Clock clock) {
        this(consultaRepository, restClient, clock,
                new CircuitBreakerIa("predicao-no-show", new IaProperties.CircuitBreaker(), clock));
    }

    NoShowPredictorService(ConsultaRepository consultaRepository, RestClient restClient, Clock clock,
                           CircuitBreakerIa circuitBreaker) {
        this.consultaRepository = consultaRepository;
        this.restClient = restClient;
        this.clock = clock;
        this.circuitBreaker = circuitBreaker;
    }

    public PredicaoNoShowDTO calcular(Consulta consulta) {
        if (!circuitBreaker.permitirChamada()) {
            log.warn("Circuit breaker da predicao aberto - score indisponivel sem chamar o microservico");
            return indisponivel();
        }
        try {
            PredicaoRequest request = montarRequest(consulta);
            PredicaoResponse response = restClient.post()
                    .uri("/predict")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(PredicaoResponse.class);

            if (!respostaValida(response)) {
                log.warn("Microservico de no-show devolveu resposta invalida");
                circuitBreaker.registrarFalha();
                return indisponivel();
            }
            circuitBreaker.registrarSucesso();
            return new PredicaoNoShowDTO(response.score(), response.categoria(), response.motivo());
        } catch (RuntimeException exception) {
            log.warn("Microservico de no-show indisponivel: {}", exception.getClass().getSimpleName());
            circuitBreaker.registrarFalha();
            return indisponivel();
        }
    }

    public CircuitBreakerIa.Metricas obterMetricas() {
        return circuitBreaker.obterMetricas();
    }

    private PredicaoRequest montarRequest(Consulta consulta) {
        LocalDateTime agora = LocalDateTime.now(clock);
        List<Consulta> anteriores = consultaRepository
                .findByPetTutorIdOrderByDataHoraDesc(consulta.getPet().getTutor().getId())
                .stream()
                .filter(item -> item != consulta)
                .filter(item -> consulta.getId() == null || !consulta.getId().equals(item.getId()))
                .filter(item -> item.getDataHora() != null && item.getDataHora().isBefore(agora))
                .toList();

        long faltas = anteriores.stream()
                .filter(item -> item.getStatus() == StatusConsulta.NAO_COMPARECEU)
                .count();
        long antecedencia = Math.max(0, ChronoUnit.DAYS.between(
                LocalDate.now(clock), consulta.getDataHora().toLocalDate()));
        int idadePet = consulta.getPet().getIdade() == null
                ? 0
                : Math.max(0, consulta.getPet().getIdade());

        return new PredicaoRequest(
                inteiroSeguro(anteriores.size()),
                inteiroSeguro(faltas),
                inteiroSeguro(antecedencia),
                periodo(consulta.getDataHora().toLocalTime()),
                0, // o modelo atual nao persiste historico de confirmacao de lembretes
                idadePet);
    }

    private static RestClient criarCliente(RestClient.Builder builder, String baseUrl, long timeoutMs) {
        Duration timeout = Duration.ofMillis(Math.max(1, timeoutMs));
        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    private boolean respostaValida(PredicaoResponse response) {
        return response != null
                && response.score() != null
                && Double.isFinite(response.score())
                && response.score() >= 0.0
                && response.score() <= 1.0
                && CATEGORIAS_VALIDAS.contains(response.categoria())
                && response.motivo() != null
                && !response.motivo().isBlank();
    }

    private PredicaoNoShowDTO indisponivel() {
        return new PredicaoNoShowDTO(null, CATEGORIA_INDISPONIVEL, MOTIVO_INDISPONIVEL);
    }

    private int periodo(LocalTime horario) {
        if (horario.isBefore(LocalTime.NOON)) return 0;
        if (horario.isBefore(LocalTime.of(17, 0))) return 1;
        return 2;
    }

    private int inteiroSeguro(long valor) {
        return valor > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) valor;
    }

    private record PredicaoRequest(
            int consultas_anteriores,
            int faltas_anteriores,
            int dias_antecedencia,
            int periodo,
            int ja_confirmou_antes,
            int idade_pet) {
    }

    private record PredicaoResponse(Double score, String categoria, String motivo) {
    }
}

