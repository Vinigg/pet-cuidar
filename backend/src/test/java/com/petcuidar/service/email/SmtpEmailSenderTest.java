package com.petcuidar.service.email;

import com.petcuidar.config.EmailProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpEmailSenderTest {
    @Test
    void enviaCodigoComDestinatarioRemetenteAssuntoETexto() throws Exception {
        JavaMailSender smtp = mock(JavaMailSender.class);
        var properties = new EmailProperties();
        properties.setFrom("acesso@petcuidar.local");
        new SmtpEmailSender(smtp, properties).enviar(
                "tutor@petcuidar.local", "Código de acesso", "Seu código é 12345678.", "desafio-1");
        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(smtp).send(captor.capture());
        var mensagem = captor.getValue();
        assertArrayEquals(new String[]{"tutor@petcuidar.local"}, mensagem.getTo());
        assertEquals("acesso@petcuidar.local", mensagem.getFrom());
        assertEquals("Código de acesso", mensagem.getSubject());
        assertEquals("Seu código é 12345678.", mensagem.getText());
    }

    @Test
    void falhaDeSmtpPermiteTratamentoDeFalhaDeEntrega() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        doThrow(new MailSendException("Conexão recusada")).when(smtp).send(any(SimpleMailMessage.class));
        var sender = new SmtpEmailSender(smtp, new EmailProperties());
        var erro = assertThrows(EmailDeliveryException.class,
                () -> sender.enviar("tutor@petcuidar.local", "Código", "Texto", "desafio-2"));
        assertTrue(erro.isTransitoria());
    }

    @Test
    void springSelecionaSmtpComConfiguracaoDoMailpit() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withPropertyValues("email.provider=smtp", "spring.mail.host=localhost", "spring.mail.port=1025")
                .withBean(EmailProperties.class)
                .withUserConfiguration(SmtpEmailSender.class, LocalEmailSender.class)
                .run(contexto -> {
                    assertNull(contexto.getStartupFailure());
                    assertInstanceOf(SmtpEmailSender.class, contexto.getBean(EmailSender.class));
                });
    }
}
