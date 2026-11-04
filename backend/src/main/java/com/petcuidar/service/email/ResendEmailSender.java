package com.petcuidar.service.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcuidar.config.EmailProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "email.provider", havingValue = "resend")
public class ResendEmailSender implements EmailSender {
    private final EmailProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ResendEmailSender(EmailProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
    }

    ResendEmailSender(EmailProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public void enviar(String destinatario, String assunto, String texto, String chaveIdempotencia)
            throws EmailDeliveryException {
        String apiKey = properties.getResend().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new EmailDeliveryException("Credencial do provedor de e-mail nao configurada", false);
        }

        final String json;
        try {
            json = objectMapper.writeValueAsString(Map.of(
                    "from", properties.getFrom(),
                    "to", new String[]{destinatario},
                    "subject", assunto,
                    "text", texto));
        } catch (JsonProcessingException e) {
            throw new EmailDeliveryException("Falha ao montar requisicao de e-mail", false, e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getResend().getBaseUrl() + "/emails"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", chaveIdempotencia)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                boolean transitoria = status == 408 || status == 429 || status >= 500;
                throw new EmailDeliveryException("Provedor de e-mail respondeu HTTP " + status, transitoria);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmailDeliveryException("Envio de e-mail interrompido", true, e);
        } catch (IOException e) {
            throw new EmailDeliveryException("Provedor de e-mail indisponivel", true, e);
        }
    }
}
