package com.petcuidar.service.ia;

/**
 * O provedor respondeu, mas sem texto utilizavel (vazio, recusa, truncado).
 * Uso interno da camada de IA: e sempre resolvida com o template de fallback
 * e nunca chega ao handler HTTP.
 */
public class RespostaIaInvalidaException extends RuntimeException {
    public RespostaIaInvalidaException(String message) { super(message); }
}
