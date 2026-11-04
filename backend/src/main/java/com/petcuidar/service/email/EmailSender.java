package com.petcuidar.service.email;

public interface EmailSender {
    void enviar(String destinatario, String assunto, String texto, String chaveIdempotencia)
            throws EmailDeliveryException;
}
