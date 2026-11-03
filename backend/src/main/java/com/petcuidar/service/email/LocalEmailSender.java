package com.petcuidar.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "email.provider", havingValue = "local", matchIfMissing = true)
public class LocalEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(LocalEmailSender.class);

    @Override
    public void enviar(String destinatario, String assunto, String texto, String chaveIdempotencia) {
        log.info("Envio de e-mail simulado localmente para destinatario mascarado {}", mascarar(destinatario));
    }

    private String mascarar(String email) {
        int arroba = email == null ? -1 : email.indexOf('@');
        return arroba <= 1 ? "***" : email.charAt(0) + "***" + email.substring(arroba);
    }
}
