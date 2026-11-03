package com.petcuidar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "email")
public class EmailProperties {
    private String provider = "local";
    private String from = "no-reply@petcuidar.local";
    private String subject = "Lembrete da consulta do seu pet";
    private Retry retry = new Retry();
    private Resend resend = new Resend();

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public Retry getRetry() { return retry; }
    public void setRetry(Retry retry) { this.retry = retry; }
    public Resend getResend() { return resend; }
    public void setResend(Resend resend) { this.resend = resend; }

    public static class Retry {
        private int maxAttempts = 3;
        private long initialBackoffMs = 250;

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public long getInitialBackoffMs() { return initialBackoffMs; }
        public void setInitialBackoffMs(long initialBackoffMs) { this.initialBackoffMs = initialBackoffMs; }
    }

    public static class Resend {
        private String baseUrl = "https://api.resend.com";
        private String apiKey = "";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }
}
