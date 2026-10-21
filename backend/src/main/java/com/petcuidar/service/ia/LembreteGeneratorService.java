package com.petcuidar.service.ia;

import com.petcuidar.config.IaProperties;
import com.petcuidar.model.Consulta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

/**
 * Geracao do texto de lembrete via IA (US3.1).
 *
 * Padrao aplicado (Secao 3.4 / 4.2 do documento):
 *  1. Chama o modelo de linguagem com timeout curto.
 *  2. Guardrail: valida deterministicamente se o texto gerado contem o
 *     nome do pet, do tutor e a data/hora corretos.
 *  3. Se a chamada falhar, estourar o timeout, ou o guardrail rejeitar o
 *     texto, usa-se SEMPRE o template fixo de fallback - o lembrete nunca
 *     deixa de ser enviado por causa de um problema na IA.
 *  4. Um "circuit breaker" simples interrompe as chamadas ao modelo apos
 *     N falhas seguidas, evitando penalizar cada requisicao com o timeout
 *     inteiro enquanto o servico externo estiver fora do ar.
 */
@Service
public class LembreteGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(LembreteGeneratorService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm");

    private final IaProperties props;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // Estado simples do circuit breaker (em memoria; para producao, mover para Redis
    // ou usar uma lib dedicada como resilience4j em ambiente multi-instancia)
    private int falhasSeguidas = 0;
    private long circuitoAbertoAte = 0;

    public LembreteGeneratorService(IaProperties props) {
        this.props = props;
    }

    public static class ResultadoLembrete {
        public final String textoGeradoIa; // pode ser null se a chamada falhou
        public final String textoValidado; // o que de fato sera enviado
        public final boolean usouFallback;

        public ResultadoLembrete(String textoGeradoIa, String textoValidado, boolean usouFallback) {
            this.textoGeradoIa = textoGeradoIa;
            this.textoValidado = textoValidado;
            this.usouFallback = usouFallback;
        }
    }

    public ResultadoLembrete gerar(Consulta consulta) {
        String nomeTutor = consulta.getPet().getTutor().getNome();
        String nomePet = consulta.getPet().getNome();
        String dataHoraFmt = consulta.getDataHora().format(FMT);
        String veterinario = consulta.getVeterinario().getNome();

        String fallback = montarTemplateFallback(nomeTutor, nomePet, dataHoraFmt, veterinario);

        if (circuitoAberto()) {
            log.warn("Circuit breaker de IA aberto - usando fallback direto para consulta {}", consulta.getId());
            return new ResultadoLembrete(null, fallback, true);
        }

        String textoGerado;
        try {
            textoGerado = chamarModeloComTimeout(nomeTutor, nomePet, dataHoraFmt, veterinario);
            registrarSucesso();
        } catch (Exception e) {
            log.warn("Falha ao gerar lembrete via IA para consulta {}: {}", consulta.getId(), e.getMessage());
            registrarFalha();
            return new ResultadoLembrete(null, fallback, true);
        }

        if (!guardrailAprova(textoGerado, nomeTutor, nomePet, dataHoraFmt)) {
            log.warn("Guardrail rejeitou o texto gerado pela IA para consulta {} - usando fallback", consulta.getId());
            return new ResultadoLembrete(textoGerado, fallback, true);
        }

        return new ResultadoLembrete(textoGerado, textoGerado, false);
    }

    /**
     * Chamada ao modelo de linguagem com timeout. Neste projeto de referencia a
     * chamada e simulada (sem custo/API key) para manter o repositorio
     * executavel sem segredos; em producao, o corpo deste metodo faria uma
     * chamada HTTP (RestTemplate/WebClient) a API do provedor de IA (ex.:
     * Anthropic ou OpenAI), enviando o prompt estruturado descrito no
     * repositorio de prompts do documento de entrega.
     */
    private String chamarModeloComTimeout(String nomeTutor, String nomePet, String dataHoraFmt, String veterinario)
            throws TimeoutException, ExecutionException, InterruptedException {
        Future<String> future = executor.submit(() -> simularChamadaIa(nomeTutor, nomePet, dataHoraFmt, veterinario));
        return future.get(props.getTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    private String simularChamadaIa(String nomeTutor, String nomePet, String dataHoraFmt, String veterinario) {
        // Simulacao local do texto que um LLM geraria a partir do prompt de
        // "Gerar lembrete de consulta" (ver documento, Secao 5.1).
        return String.format(
                "Ola %s! Passando para lembrar que %s tem consulta marcada com %s no dia %s. Ate la!",
                nomeTutor, nomePet, veterinario, dataHoraFmt);
    }

    private boolean guardrailAprova(String texto, String nomeTutor, String nomePet, String dataHoraFmt) {
        if (texto == null) return false;
        return texto.contains(nomeTutor) && texto.contains(nomePet) && texto.contains(dataHoraFmt);
    }

    private String montarTemplateFallback(String nomeTutor, String nomePet, String dataHoraFmt, String veterinario) {
        return String.format(
                "Ola %s, lembramos que %s tem consulta agendada com %s em %s. Contamos com voces!",
                nomeTutor, nomePet, veterinario, dataHoraFmt);
    }

    private synchronized boolean circuitoAberto() {
        return System.currentTimeMillis() < circuitoAbertoAte;
    }

    private synchronized void registrarFalha() {
        falhasSeguidas++;
        if (falhasSeguidas >= props.getCircuitBreaker().getMaxFalhas()) {
            circuitoAbertoAte = System.currentTimeMillis() + props.getCircuitBreaker().getResetMs();
            log.warn("Circuit breaker de IA ABERTO por {} ms apos {} falhas seguidas",
                    props.getCircuitBreaker().getResetMs(), falhasSeguidas);
        }
    }

    private synchronized void registrarSucesso() {
        falhasSeguidas = 0;
    }
}
