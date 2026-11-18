package com.petcuidar.service.ia;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.petcuidar.config.IaProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.stream.Collectors;

/**
 * Integracao real com LLM (PC-030) via SDK oficial da Anthropic.
 * Ativa quando ia.provider=llm-externo (bean registrado por
 * {@link com.petcuidar.config.IaClientConfig}). Timeout, circuit breaker,
 * guardrail e fallback continuam no {@link LembreteGeneratorService};
 * aqui ficam apenas o prompt, a chamada e a leitura da resposta.
 *
 * - Chave somente por variavel de ambiente (LLM_API_KEY); sem chave, cada
 *   chamada falha e o gerador usa o template - a aplicacao continua no ar.
 * - Sem retentativas do SDK: o timeout de ia.timeout-ms vale para a chamada
 *   inteira e falhas repetidas sao tratadas pelo circuit breaker.
 * - Envia apenas nomes, data e horario; nunca e-mail, telefone ou CPF.
 * - Nenhum log contem a chave, o prompt ou o texto gerado.
 */
public class AnthropicLembreteIaClient implements LembreteIaClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLembreteIaClient.class);
    // Em caso de recusa do modelo, a API repete a chamada em um modelo alternativo.
    static final String BETA_FALLBACK_SERVIDOR = "server-side-fallback-2026-07-01";

    private final AnthropicClient client;
    private final IaProperties.Llm config;
    private final String nomeClinica;

    public AnthropicLembreteIaClient(IaProperties props, String nomeClinica) {
        this(criarCliente(props), props.getLlm(), nomeClinica);
    }

    AnthropicLembreteIaClient(AnthropicClient client, IaProperties.Llm config, String nomeClinica) {
        this.client = client;
        this.config = config;
        this.nomeClinica = nomeClinica == null || nomeClinica.isBlank() ? "Pet Cuidar" : nomeClinica.strip();
        if (client == null) {
            log.warn("Provedor de IA 'llm-externo' sem LLM_API_KEY: lembretes usarao o template de fallback.");
        } else {
            log.info("Lembretes via LLM habilitados (provedor=llm-externo, modelo={})", config.getModel());
        }
    }

    static AnthropicClient criarCliente(IaProperties props) {
        String apiKey = props.getLlm().getApiKey();
        if (apiKey == null || apiKey.isBlank()) return null;
        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(props.getLlm().getBaseUrl())
                .timeout(Duration.ofMillis(props.getTimeoutMs()))
                .maxRetries(0)
                .build();
    }

    @Override
    public String gerar(DadosLembrete dados) {
        if (client == null) {
            throw new IllegalStateException("LLM_API_KEY nao configurada");
        }
        BetaMessage resposta = client.beta().messages().create(montarRequisicao(dados));

        BetaStopReason motivoParada = resposta.stopReason().orElse(null);
        if (BetaStopReason.REFUSAL.equals(motivoParada)) {
            throw new RespostaIaInvalidaException("modelo recusou a solicitacao");
        }
        if (BetaStopReason.MAX_TOKENS.equals(motivoParada)) {
            throw new RespostaIaInvalidaException("resposta truncada por max_tokens");
        }
        String texto = resposta.content().stream()
                .map(BetaContentBlock::text)
                .flatMap(java.util.Optional::stream)
                .map(bloco -> bloco.text())
                .collect(Collectors.joining("\n"))
                .strip();
        if (texto.isEmpty()) {
            throw new RespostaIaInvalidaException("resposta sem texto");
        }
        return texto;
    }

    MessageCreateParams montarRequisicao(DadosLembrete dados) {
        return MessageCreateParams.builder()
                .model(config.getModel())
                .maxTokens(config.getMaxTokens())
                .outputConfig(BetaOutputConfig.builder()
                        .effort(BetaOutputConfig.Effort.of(config.getEffort()))
                        .build())
                .addBeta(BETA_FALLBACK_SERVIDOR)
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .system(promptSistema())
                .addUserMessage(promptUsuario(dados))
                .build();
    }

    String promptSistema() {
        return """
                Você redige lembretes de consulta veterinária enviados pela clínica %s aos tutores.
                Escreva em português do Brasil, em tom cordial, claro e objetivo, uma única mensagem \
                de no máximo três frases e até 400 caracteres, em texto simples, sem markdown e sem emojis.

                Regras obrigatórias:
                - Copie exatamente, sem alterar grafia nem formato, o nome do tutor, o nome do pet, \
                a data (dd/mm/aaaa) e o horário (hh:mm) informados.
                - Use somente as informações fornecidas. Não acrescente outras datas, horários, números, \
                endereços, telefones, links, e-mails, valores, preparos, orientações clínicas ou nomes.
                - Se citar o veterinário, use exatamente o nome informado.
                - Responda apenas com o texto do lembrete.""".formatted(nomeClinica);
    }

    String promptUsuario(DadosLembrete dados) {
        StringBuilder prompt = new StringBuilder("Dados da consulta:\n")
                .append("- Tutor: ").append(dados.nomeTutor()).append('\n')
                .append("- Pet: ").append(dados.nomePet()).append('\n');
        if (dados.possuiVeterinario()) {
            prompt.append("- Veterinário: ").append(dados.nomeVeterinario()).append('\n');
        }
        return prompt.append("- Data: ").append(dados.dataFormatada()).append('\n')
                .append("- Horário: ").append(dados.horaFormatada())
                .toString();
    }

    @PreDestroy
    public void encerrar() {
        if (client != null) client.close();
    }
}
