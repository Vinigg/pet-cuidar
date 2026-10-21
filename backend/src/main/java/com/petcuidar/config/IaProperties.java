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
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
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
