package com.petcuidar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuracoes das integracoes de IA (timeout e "circuit breaker" simples),
 * conforme descrito na Secao 4 do DAS/Entrega GrowUp: nenhuma chamada de IA
 * pode travar a requisicao do usuario final - sempre ha um limite de tempo
 * e um fallback deterministico.
 */
@Component
@ConfigurationProperties(prefix = "ia")
public class IaProperties {

    private long timeoutMs = 5000;
    private String provider = "simulator";
    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

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

    public static class CircuitBreaker {
        private int maxFalhas = 3;
        private long resetMs = 30000;

        public int getMaxFalhas() { return maxFalhas; }
        public void setMaxFalhas(int maxFalhas) { this.maxFalhas = maxFalhas; }
        public long getResetMs() { return resetMs; }
        public void setResetMs(long resetMs) { this.resetMs = resetMs; }
    }
}
