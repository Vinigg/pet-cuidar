package com.petcuidar.service.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.petcuidar.config.IaProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Adaptador HTTP para APIs compatíveis com OpenAI. A chave vem exclusivamente
 * de configuração externa; respostas brutas nunca chegam diretamente ao controller.
 */
@Component
@ConditionalOnProperty(name = "ia.provider", havingValue = "openai")
public class OpenAiIaClient implements LembreteIaClient, ResumoIaClient {

    private final IaProperties props;
    private final RestClient restClient;

    public OpenAiIaClient(IaProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(props.getTimeoutMs());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + props.getApiKey())
                .build();
    }

    @Override
    public String gerar(DadosLembrete dados) {
        String prompt = "Escreva um lembrete cordial de consulta veterinaria com no maximo 300 caracteres. "
                + "Inclua literalmente tutor=" + dados.nomeTutor() + ", pet=" + dados.nomePet()
                + ", data_hora=" + dados.dataFormatada() + " as " + dados.horaFormatada()
                + (dados.possuiVeterinario() ? " e veterinario=" + dados.nomeVeterinario() : "")
                + ". Nao invente informacoes.";
        return completar(prompt);
    }

    @Override
    public String resumir(String nomePet, List<String> atendimentos, String itensCriticos) {
        String prompt = "Resuma em 2 ou 3 frases o historico veterinario fornecido, sem diagnosticar "
                + "nem inventar fatos. Pet=" + nomePet + ". Atendimentos=" + atendimentos
                + ". Itens criticos cadastrados=" + itensCriticos;
        return completar(prompt);
    }

    private String completar(String prompt) {
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new IllegalStateException("LLM_API_KEY nao configurada");
        }
        JsonNode response = restClient.post()
                .uri("/chat/completions")
                .body(Map.of(
                        "model", props.getModel(),
                        "temperature", 0.2,
                        "messages", List.of(
                                Map.of("role", "system", "content",
                                        "Responda apenas com o texto solicitado, em portugues brasileiro."),
                                Map.of("role", "user", "content", prompt))))
                .retrieve()
                .body(JsonNode.class);
        JsonNode content = response == null ? null : response.at("/choices/0/message/content");
        if (content == null || content.isMissingNode() || content.asText().isBlank()) {
            throw new IllegalStateException("Provedor de IA retornou resposta vazia");
        }
        return content.asText().trim();
    }
}
