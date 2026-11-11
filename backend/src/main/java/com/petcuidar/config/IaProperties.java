package com.petcuidar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuracoes das integracoes de IA (provedor, timeout e circuit breaker),
 * conforme a Secao 4 do DAS/Entrega GrowUp: nenhuma chamada de IA pode travar
 * a requisicao do usuario final - sempre ha um limite de tempo e um fallback
 * deterministico.
 *
 * <p>Provedor (exatamente um, escolhido em {@link IaClientConfig}):
 * {@code ia.provider} = {@code simulador} (padrao), {@code openai} ou
 * {@code llm-externo}. Cada provedor tem as suas proprias propriedades:
 * {@code ia.base-url}/{@code ia.api-key}/{@code ia.model} para {@code openai}
 * e {@code ia.llm.*} para {@code llm-externo}.</p>
 *
 * <p>Timeout e circuit breaker: {@code ia.timeout-ms} e
 * {@code ia.circuit-breaker.*} sao os padroes; cada integracao
 * ({@code ia.lembrete}, {@code ia.resumo}, {@code ia.predicao}) pode
 * sobrescreve-los.</p>
 */
@Component
@ConfigurationProperties(prefix = "ia")
public class IaProperties {

    private long timeoutMs = 5000;
    private String provider = "simulador";
    // Propriedades exclusivas do provedor "openai" (API compativel com /chat/completions).
    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private CircuitBreaker circuitBreaker = new CircuitBreaker();
    private Llm llm = new Llm();
    private Integracao lembrete = new Integracao();
    private Integracao resumo = new Integracao();
    private Integracao predicao = new Integracao();

    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public CircuitBreaker getCircuitBreaker() { return circuitBreaker; }
    public void setCircuitBreaker(CircuitBreaker circuitBreaker) { this.circuitBreaker = circuitBreaker; }
    public Llm getLlm() { return llm; }
    public void setLlm(Llm llm) { this.llm = llm; }
    public Integracao getLembrete() { return lembrete; }
    public void setLembrete(Integracao lembrete) { this.lembrete = lembrete; }
    public Integracao getResumo() { return resumo; }
    public void setResumo(Integracao resumo) { this.resumo = resumo; }
    public Integracao getPredicao() { return predicao; }
    public void setPredicao(Integracao predicao) { this.predicao = predicao; }

    /** Timeout efetivo da integracao: o valor proprio ou, se ausente, {@code ia.timeout-ms}. */
    public long timeoutMs(Integracao integracao) {
        return integracao.getTimeoutMs() != null ? integracao.getTimeoutMs() : timeoutMs;
    }

    /** Circuit breaker efetivo da integracao, completando o que faltar com {@code ia.circuit-breaker.*}. */
    public CircuitBreaker circuitBreaker(Integracao integracao) {
        CircuitBreakerIntegracao proprio = integracao.getCircuitBreaker();
        CircuitBreaker efetivo = new CircuitBreaker();
        efetivo.setMaxFalhas(proprio.getMaxFalhas() != null ? proprio.getMaxFalhas() : circuitBreaker.getMaxFalhas());
        efetivo.setResetMs(proprio.getResetMs() != null ? proprio.getResetMs() : circuitBreaker.getResetMs());
        return efetivo;
    }

    /**
     * Propriedades exclusivas do provedor {@code llm-externo}. Funciona sem
     * credenciais: sem {@code LLM_API_KEY}, cada chamada falha e o lembrete
     * sai pelo template. A chave vem somente de variavel de ambiente - nunca
     * de arquivo versionado.
     *
     * <p>{@code provider} e mantido por compatibilidade com configuracoes
     * antigas ({@code ia.llm.provider}); a selecao e feita em {@link IaClientConfig}.</p>
     */
    public static class Llm {
        private String provider = "";
        private String apiKey = "";
        private String model = "claude-opus-5-5";
        private String baseUrl = "https://api.anthropic.com";
        private long maxTokens = 2048;
        private String effort = "low";

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public long getMaxTokens() { return maxTokens; }
        public void setMaxTokens(long maxTokens) { this.maxTokens = maxTokens; }
        public String getEffort() { return effort; }
        public void setEffort(String effort) { this.effort = effort; }
    }

    public static class CircuitBreaker {
        private int maxFalhas = 3;
        private long resetMs = 30000;

        public int getMaxFalhas() { return maxFalhas; }
        public void setMaxFalhas(int maxFalhas) { this.maxFalhas = maxFalhas; }
        public long getResetMs() { return resetMs; }
        public void setResetMs(long resetMs) { this.resetMs = resetMs; }
    }

    /** Ajustes de uma integracao. Campos nulos usam os padroes globais. */
    public static class Integracao {
        private Long timeoutMs;
        private CircuitBreakerIntegracao circuitBreaker = new CircuitBreakerIntegracao();

        public Long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(Long timeoutMs) { this.timeoutMs = timeoutMs; }
        public CircuitBreakerIntegracao getCircuitBreaker() { return circuitBreaker; }
        public void setCircuitBreaker(CircuitBreakerIntegracao circuitBreaker) { this.circuitBreaker = circuitBreaker; }
    }

    public static class CircuitBreakerIntegracao {
        private Integer maxFalhas;
        private Long resetMs;

        public Integer getMaxFalhas() { return maxFalhas; }
        public void setMaxFalhas(Integer maxFalhas) { this.maxFalhas = maxFalhas; }
        public Long getResetMs() { return resetMs; }
        public void setResetMs(Long resetMs) { this.resetMs = resetMs; }
    }
}
