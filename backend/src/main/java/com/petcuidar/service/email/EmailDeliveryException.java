package com.petcuidar.service.email;

public class EmailDeliveryException extends Exception {
    private final boolean transitoria;

    public EmailDeliveryException(String message, boolean transitoria) {
        super(message);
        this.transitoria = transitoria;
    }

    public EmailDeliveryException(String message, boolean transitoria, Throwable cause) {
        super(message, cause);
        this.transitoria = transitoria;
    }

    public boolean isTransitoria() { return transitoria; }
}
