package com.petcuidar.service.email;

public class EmailDeliveryException extends Exception {
    /** Mensagem gravada em Lembrete.ultimoErro quando a falha foi por tempo limite. */
    public static final String MENSAGEM_TIMEOUT = "Provedor de e-mail nao respondeu no tempo limite";

    private final boolean transitoria;
    private final boolean timeout;

    public EmailDeliveryException(String message, boolean transitoria) {
        this(message, transitoria, null);
    }

    public EmailDeliveryException(String message, boolean transitoria, Throwable cause) {
        this(message, transitoria, false, cause);
    }

    private EmailDeliveryException(String message, boolean transitoria, boolean timeout, Throwable cause) {
        super(message, cause);
        this.transitoria = transitoria;
        this.timeout = timeout;
    }

    /** Falha transitoria por tempo limite (conexao ou resposta). */
    public static EmailDeliveryException tempoEsgotado(Throwable cause) {
        return new EmailDeliveryException(MENSAGEM_TIMEOUT, true, true, cause);
    }

    public boolean isTransitoria() { return transitoria; }

    public boolean isTimeout() { return timeout; }

    /** Indica se o ultimo erro registrado em um lembrete foi tempo limite. */
    public static boolean indicaTimeout(String ultimoErro) {
        return MENSAGEM_TIMEOUT.equals(ultimoErro);
    }
}
