package com.petcuidar.service.email;

import com.petcuidar.config.EmailProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "email.provider", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {
    private final JavaMailSender remetente;
    private final EmailProperties properties;

    public SmtpEmailSender(JavaMailSender remetente, EmailProperties properties) {
        this.remetente = remetente;
        this.properties = properties;
    }

    @Override
    public void enviar(String destinatario, String assunto, String texto, String chaveIdempotencia)
            throws EmailDeliveryException {
        var mensagem = new SimpleMailMessage();
        mensagem.setFrom(properties.getFrom());
        mensagem.setTo(destinatario);
        mensagem.setSubject(assunto);
        mensagem.setText(texto);
        try {
            remetente.send(mensagem);
        } catch (MailException e) {
            throw new EmailDeliveryException("Falha ao entregar e-mail ao servidor SMTP", true, e);
        }
    }
}
